package com.pavex.auth.service

import com.pavex.auth.exception.InvalidCurrentPasswordException
import com.pavex.auth.exception.PasswordUnchangedException
import com.pavex.auth.repository.RefreshSessionRepository
import com.pavex.auth.repository.UserAccountRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ChangePasswordService(
    private val advisoryLockService:
    AdvisoryLockService,
    private val userAccountRepository:
        UserAccountRepository,

    private val refreshSessionRepository:
        RefreshSessionRepository,

    private val passwordEncoder:
        PasswordEncoder,
) {

    @Transactional
    fun changePassword(
        userId: UUID,
        currentPassword: String,
        newPassword: String,
    ) {
        advisoryLockService
            .lockUserSessions(
                userId
            )
        val account =
            userAccountRepository
                .findById(userId)
                .orElseThrow {
                    InvalidCurrentPasswordException()
                }

        val currentMatches =
            passwordEncoder.matches(
                currentPassword,
                account.passwordHash,
            )

        if (!currentMatches) {
            throw InvalidCurrentPasswordException()
        }

        /*
         * Không cho đổi:
         *
         * oldPassword -> oldPassword
         */
        val newMatchesCurrent =
            passwordEncoder.matches(
                newPassword,
                account.passwordHash,
            )

        if (newMatchesCurrent) {
            throw PasswordUnchangedException()
        }

        val now =
            Instant.now()

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

        /*
         * Password thay đổi -> mọi refresh session cũ
         * đều không còn hợp lệ.
         *
         * User phải login lại trên các thiết bị.
         */
        refreshSessionRepository
            .revokeAllActiveByUserId(
                userId =
                    userId,

                now =
                    now,
            )
    }
}
