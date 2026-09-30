package com.pavex.auth.service

import com.pavex.auth.entity.InvitationToken
import com.pavex.auth.event.InvitationRequestedEvent
import com.pavex.auth.exception.AccountAlreadyActivatedException
import com.pavex.auth.exception.InvalidInvitationTokenException
import com.pavex.auth.exception.UserCannotBeInvitedException
import com.pavex.auth.exception.UserNotFoundException
import com.pavex.auth.repository.InvitationTokenRepository
import com.pavex.auth.repository.UserAccountRepository
import com.pavex.auth.security.OpaqueTokenService
import com.pavex.auth.messaging.AuthMessagingTopology
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class InvitationService(

    private val invitationTokenRepository:
        InvitationTokenRepository,

    private val userAccountRepository:
        UserAccountRepository,

    private val opaqueTokenService:
        OpaqueTokenService,

    private val passwordEncoder:
        PasswordEncoder,

    private val outboxService:
        OutboxService,

    private val advisoryLockService:
        AdvisoryLockService,

    private val jwtService:
        com.pavex.auth.security.JwtService,

    private val identityStateSyncService:
        IdentityStateSyncService,

    @Value(
        "\${auth.security.invitation-token-ttl:PT24H}"
    )
    private val invitationTokenTtl:
        Duration,
) {

    @Transactional
    fun sendInvite(
        userId: UUID,
    ) {

        /*
         * userId đã biết từ URL, lock trước khi load entity
         * để state đọc sau khi chờ lock là state mới nhất.
         */
        advisoryLockService
            .lockInvitation(
                userId
            )

        val user =
            userAccountRepository
                .findById(
                    userId
                )
                .orElseThrow {
                    UserNotFoundException()
                }

        val authorization =
            jwtService.resolveAuthorization(
                user
            )

        if (authorization.emailVerified) {
            throw AccountAlreadyActivatedException()
        }

        if (user.email.isBlank()) {
            throw UserCannotBeInvitedException()
        }

        val now =
            Instant.now()

        invitationTokenRepository
            .invalidateUnusedByUserId(
                userId =
                    user.id,
                now =
                    now,
            )

        val issued =
            opaqueTokenService.issue()

        invitationTokenRepository.save(
            InvitationToken(
                userId =
                    user.id,
                tokenHash =
                    issued.hash,
                expiresAt =
                    now.plus(
                        invitationTokenTtl
                    ),
                createdAt =
                    now,
            )
        )

        outboxService.enqueue(
            eventType =
                AuthMessagingTopology.INVITATION_REQUESTED,
            payload =
                InvitationRequestedEvent(
                    email = user.email,
                    rawToken = issued.raw,
                ),
            aggregateType = "user",
            aggregateId = user.id,
            occurredAt = now,
        )
    }


    @Transactional
    fun acceptInvite(
        rawTokenInput: String,
        password: String,
    ) {

        val rawToken =
            rawTokenInput.trim()

        val tokenHash =
            opaqueTokenService.hash(
                rawToken
            )

        val invitation =
            invitationTokenRepository
                .findByTokenHash(
                    tokenHash
                )
                ?: throw InvalidInvitationTokenException()

        advisoryLockService
            .lockInvitation(
                invitation.userId
            )

        val now =
            Instant.now()

        val consumed =
            invitationTokenRepository
                .consumeIfValid(
                    id =
                        invitation.id,
                    now =
                        now,
                )

        if (consumed != 1) {
            throw InvalidInvitationTokenException()
        }

        val user =
            userAccountRepository
                .findById(
                    invitation.userId
                )
                .orElseThrow {
                    InvalidInvitationTokenException()
                }

        val authorization =
            jwtService.resolveAuthorization(
                user
            )

        if (authorization.emailVerified) {
            throw AccountAlreadyActivatedException()
        }

        user.passwordHash =
            requireNotNull(
                passwordEncoder.encode(
                    password
                )
            ) {
                "Password encoder returned null"
            }

        user.emailVerified = true
        user.failedLoginAttempts = 0
        user.mustChangePassword = false
        user.temporaryPasswordExpiresAt = null
        user.lockoutEndAt = null
        user.updatedAt = now

        userAccountRepository.save(
            user
        )

        invitationTokenRepository
            .invalidateUnusedByUserId(
                userId =
                    user.id,
                now =
                    now,
            )

        identityStateSyncService
            .emailVerifiedAfterCommit(
                user.id
            )
    }
}
