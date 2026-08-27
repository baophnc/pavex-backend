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

    @Value(
        "\${identity.security.password-reset-token-ttl:PT1H}"
    )
    private val passwordResetTokenTtl: Duration,
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
                .findByEmail(email)
                ?: return

        val now =
            Instant.now()

        /*
         * Latest-token-only:
         * token reset cũ chưa dùng sẽ bị revoke.
         */
        passwordResetTokenRepository
            .revokeUnusedByUserId(
                account.id,
                now,
            )

        val issued =
            opaqueTokenService.issue()

        passwordResetTokenRepository.save(
            PasswordResetToken(
                userId = account.id,
                tokenHash = issued.hash,
                expiresAt =
                    now.plus(
                        passwordResetTokenTtl
                    ),
                createdAt = now,
            )
        )

        eventPublisher.publishEvent(
            PasswordResetRequestedEvent(
                email = account.email,
                rawToken = issued.raw,
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

        val now =
            Instant.now()

        val userId =
            token.userId

        val consumed =
            passwordResetTokenRepository
                .consumeIfValid(
                    token.id,
                    now,
                )

        if (consumed != 1) {
            throw InvalidPasswordResetTokenException()
        }

        val account =
            userAccountRepository
                .findById(userId)
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

        /*
         * Reset password cũng mở lockout
         * do nhập sai password trước đó.
         */
        account.failedLoginAttempts = 0
        account.lockoutEndAt = null
        account.updatedAt = now

        userAccountRepository.save(
            account
        )

        /*
         * Không token reset nào khác
         * được phép dùng sau khi password đổi.
         */
        passwordResetTokenRepository
            .revokeUnusedByUserId(
                userId,
                now,
            )

        /*
         * Bắt toàn bộ thiết bị phải login lại.
         */
        refreshSessionRepository
            .revokeAllActiveByUserId(
                userId,
                now,
            )
    }
}
