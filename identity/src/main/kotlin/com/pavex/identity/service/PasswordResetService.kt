package com.pavex.identity.service

import com.pavex.identity.entity.PasswordResetToken
import com.pavex.identity.event.PasswordResetRequestedEvent
import com.pavex.identity.exception.InvalidPasswordResetTokenException
import com.pavex.identity.repository.PasswordResetTokenRepository
import com.pavex.identity.repository.RefreshSessionRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.security.OpaqueTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
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

    private val eventPublisher:
        ApplicationEventPublisher,

    private val advisoryLockService:
        AdvisoryLockService,

    @Value(
        "\${identity.security.password-reset-token-ttl:PT1H}"
    )
    private val passwordResetTokenTtl:
        Duration,
) {

    @Transactional
    fun forgotPassword(
        emailInput: String,
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

        eventPublisher.publishEvent(
            PasswordResetRequestedEvent(
                email =
                    account.email,
                rawToken =
                    issued.raw,
            )
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
         * password-reset -> user-session.
         */
        advisoryLockService
            .lockPasswordReset(
                userId
            )

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
    }
}
