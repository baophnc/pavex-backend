package com.pavex.identity.service

import com.pavex.identity.dto.auth.ResendVerificationRequest
import com.pavex.identity.dto.auth.VerifyEmailRequest
import com.pavex.identity.entity.EmailVerificationToken
import com.pavex.identity.event.EmailVerificationRequestedEvent
import com.pavex.identity.messaging.IdentityMessagingTopology
import com.pavex.identity.exception.InvalidEmailVerificationTokenException
import com.pavex.identity.repository.EmailVerificationTokenRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.security.OpaqueTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class EmailVerificationService(

    private val emailVerificationTokenRepository:
        EmailVerificationTokenRepository,

    private val userAccountRepository:
        UserAccountRepository,

    private val opaqueTokenService:
        OpaqueTokenService,

    private val outboxService:
        OutboxService,

    private val advisoryLockService:
        AdvisoryLockService,

    @Value(
        "\${identity.security.verification-token-ttl:PT24H}"
    )
    private val verificationTokenTtl:
        Duration,

    @Value(
        "\${identity.security.verification-resend-cooldown:PT2M}"
    )
    private val verificationResendCooldown:
        Duration,
) {

    @Transactional(
        propagation = Propagation.MANDATORY,
    )
    fun issueForRegistration(
        userId: UUID,
        email: String,
        now: Instant,
    ) {

        issue(
            userId = userId,
            email = email,
            now = now,
        )
    }


    @Transactional
    fun verify(
        request: VerifyEmailRequest,
    ) {

        val rawToken =
            request.token.trim()

        if (rawToken.isBlank()) {
            throw InvalidEmailVerificationTokenException()
        }

        val tokenHash =
            opaqueTokenService.hash(
                rawToken
            )

        val verificationToken =
            emailVerificationTokenRepository
                .findByTokenHash(
                    tokenHash
                )
                ?: throw InvalidEmailVerificationTokenException()

        advisoryLockService
            .lockEmailVerification(
                verificationToken.userId
            )

        val now =
            Instant.now()

        val consumed =
            emailVerificationTokenRepository
                .consumeIfValid(
                    id =
                        verificationToken.id,
                    now =
                        now,
                )

        if (consumed != 1) {
            throw InvalidEmailVerificationTokenException()
        }

        val account =
            userAccountRepository
                .findById(
                    verificationToken.userId
                )
                .orElseThrow {
                    InvalidEmailVerificationTokenException()
                }

        if (!account.emailVerified) {

            account.emailVerified =
                true

            account.updatedAt =
                now

            userAccountRepository.save(
                account
            )
        }
    }


    @Transactional
    fun resend(
        request: ResendVerificationRequest,
    ) {

        val email =
            normalizeEmail(
                request.email
            )

        if (!looksLikeEmail(email)) {
            return
        }

        val account =
            userAccountRepository
                .findByEmail(
                    email
                )
                ?: return

        advisoryLockService
            .lockEmailVerification(
                account.id
            )

        if (account.emailVerified) {
            return
        }

        val now =
            Instant.now()

        val cooldownStart =
            now.minus(
                verificationResendCooldown
            )

        val recentRequest =
            emailVerificationTokenRepository
                .existsByUserIdAndCreatedAtGreaterThanEqual(
                    userId =
                        account.id,
                    createdAt =
                        cooldownStart,
                )

        if (recentRequest) {
            return
        }

        issue(
            userId =
                account.id,
            email =
                account.email,
            now =
                now,
        )
    }


    private fun issue(
        userId: UUID,
        email: String,
        now: Instant,
    ) {

        val issued =
            opaqueTokenService.issue()

        val verificationToken =
            EmailVerificationToken(
                userId =
                    userId,
                tokenHash =
                    issued.hash,
                expiresAt =
                    now.plus(
                        verificationTokenTtl
                    ),
                createdAt =
                    now,
            )

        emailVerificationTokenRepository.save(
            verificationToken
        )

        outboxService.enqueue(
            eventType =
                IdentityMessagingTopology.EMAIL_VERIFICATION_REQUESTED,
            payload =
                EmailVerificationRequestedEvent(
                    email = email,
                    rawToken = issued.raw,
                ),
            aggregateType = "user",
            aggregateId = userId,
            occurredAt = now,
        )
    }


    private fun normalizeEmail(
        email: String,
    ): String {

        return email
            .trim()
            .lowercase()
    }


    private fun looksLikeEmail(
        email: String,
    ): Boolean {

        val atIndex =
            email.indexOf('@')

        return atIndex > 0 &&
            atIndex < email.length - 1 &&
            email
                .substring(
                    atIndex + 1
                )
                .contains('.')
    }
}
