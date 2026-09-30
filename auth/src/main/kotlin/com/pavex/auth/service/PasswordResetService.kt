package com.pavex.auth.service

import com.pavex.auth.entity.PasswordResetToken
import com.pavex.auth.event.PasswordResetRequestedEvent
import com.pavex.auth.event.PasswordResetPortal
import com.pavex.auth.repository.InvitationTokenRepository
import com.pavex.auth.exception.InvalidPasswordResetTokenException
import com.pavex.auth.messaging.AuthMessagingTopology
import com.pavex.auth.repository.PasswordResetTokenRepository
import com.pavex.auth.repository.RefreshSessionRepository
import com.pavex.auth.repository.UserAccountRepository
import com.pavex.auth.security.OpaqueTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class PasswordResetService(

    private val passwordResetTokenRepository:
        PasswordResetTokenRepository,

    private val userAccountRepository:
        UserAccountRepository,

    private val refreshSessionRepository:
        RefreshSessionRepository,

    private val opaqueTokenService:
        OpaqueTokenService,

    private val passwordEncoder:
        PasswordEncoder,

    private val outboxService:
        OutboxService,

    private val advisoryLockService:
        AdvisoryLockService,

    private val invitationTokenRepository: InvitationTokenRepository,
    private val identityStateSyncService: IdentityStateSyncService,

    @Value(
        "\${auth.security.password-reset-token-ttl:PT1H}"
    )
    private val passwordResetTokenTtl:
        Duration,
) {

    @Transactional
    fun forgotPassword(
        emailInput: String,
        portal: PasswordResetPortal = PasswordResetPortal.CLIENT,
    ) {

        val email =
            emailInput
                .trim()
                .lowercase()

        val account =
            userAccountRepository
                .findByEmail(
                    email
                )
                ?: return

        advisoryLockService
            .lockPasswordReset(
                account.id
            )

        val now =
            Instant.now()

        /*
         * Latest-token-only.
         * Hai request forgot chạy đồng thời cũng serialize
         * theo user nên chỉ token mới nhất còn active.
         */
        passwordResetTokenRepository
            .revokeUnusedByUserId(
                userId =
                    account.id,
                now =
                    now,
            )

        val issued =
            opaqueTokenService.issue()

        passwordResetTokenRepository.save(
            PasswordResetToken(
                userId =
                    account.id,
                tokenHash =
                    issued.hash,
                expiresAt =
                    now.plus(
                        passwordResetTokenTtl
                    ),
                createdAt =
                    now,
            )
        )

        outboxService.enqueue(
            eventType =
                AuthMessagingTopology.PASSWORD_RESET_REQUESTED,
            payload =
                PasswordResetRequestedEvent(
                    email = account.email,
                    rawToken = issued.raw,
                    portal = portal,
                ),
            aggregateType = "user",
            aggregateId = account.id,
            occurredAt = now,
        )
    }


    @Transactional
    fun resetPassword(
        rawTokenInput: String,
        newPassword: String,
    ) {

        val rawToken =
            rawTokenInput.trim()

        if (rawToken.isBlank()) {
            throw InvalidPasswordResetTokenException()
        }

        val tokenHash =
            opaqueTokenService.hash(
                rawToken
            )

        val token =
            passwordResetTokenRepository
                .findByTokenHash(
                    tokenHash
                )
                ?: throw InvalidPasswordResetTokenException()

        val userId =
            token.userId

        /*
         * Thứ tự lock cố định:
         * password-reset -> invitation -> user-session.
         * Serialize with invitation acceptance before changing credentials.
         */
        advisoryLockService
            .lockPasswordReset(
                userId
            )

        advisoryLockService.lockInvitation(userId)

        advisoryLockService
            .lockUserSessions(
                userId
            )

        val now =
            Instant.now()

        val consumed =
            passwordResetTokenRepository
                .consumeIfValid(
                    id =
                        token.id,
                    now =
                        now,
                )

        if (consumed != 1) {
            throw InvalidPasswordResetTokenException()
        }

        val account =
            userAccountRepository
                .findById(
                    userId
                )
                .orElseThrow {
                    InvalidPasswordResetTokenException()
                }

        account.passwordHash =
            requireNotNull(
                passwordEncoder.encode(
                    newPassword
                )
            ) {
                "Password encoder returned null"
            }

        account.failedLoginAttempts = 0
        account.lockoutEndAt = null
        account.mustChangePassword = false
        account.temporaryPasswordExpiresAt = null
        // Possession of the emailed, single-use token proves mailbox ownership.
        // This also enables first-time password setup for admin-created users.
        // Identity account status and roles are deliberately left unchanged.
        account.emailVerified = true
        account.updatedAt = now

        userAccountRepository.save(
            account
        )

        passwordResetTokenRepository
            .revokeUnusedByUserId(
                userId =
                    userId,
                now =
                    now,
            )

        refreshSessionRepository
            .revokeAllActiveByUserId(
                userId =
                    userId,
                now =
                    now,
            )

        invitationTokenRepository.invalidateUnusedByUserId(userId, now)
        identityStateSyncService.emailVerifiedAfterCommit(userId)
    }
}
