package com.pavex.identity.service

import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.InvitationToken
import com.pavex.identity.event.InvitationRequestedEvent
import com.pavex.identity.exception.AccountAlreadyActivatedException
import com.pavex.identity.exception.InvalidInvitationTokenException
import com.pavex.identity.exception.UserCannotBeInvitedException
import com.pavex.identity.exception.UserNotFoundException
import com.pavex.identity.repository.InvitationTokenRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.security.OpaqueTokenService
import com.pavex.identity.messaging.IdentityMessagingTopology
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

    @Value(
        "\${identity.security.invitation-token-ttl:PT24H}"
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

        if (user.emailVerified) {
            throw AccountAlreadyActivatedException()
        }

        if (
            user.status !=
                AccountStatus.ACTIVE ||
            user.email.isBlank()
        ) {
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
                IdentityMessagingTopology.INVITATION_REQUESTED,
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

        if (user.emailVerified) {
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
    }
}
