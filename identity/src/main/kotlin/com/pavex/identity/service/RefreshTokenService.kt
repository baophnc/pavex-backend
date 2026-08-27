package com.pavex.identity.service

import com.pavex.identity.entity.RefreshSession
import com.pavex.identity.exception.InvalidRefreshTokenException
import com.pavex.identity.exception.RefreshTokenReuseDetectedException
import com.pavex.identity.repository.RefreshSessionRepository
import com.pavex.identity.security.OpaqueTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenService(

    private val refreshSessionRepository:
        RefreshSessionRepository,

    private val opaqueTokenService:
        OpaqueTokenService,

    private val advisoryLockService:
        AdvisoryLockService,

    @Value("\${security.jwt.refresh-token-ttl}")
    private val refreshTokenTtl:
        Duration,
) {

    @Transactional(
        propagation = Propagation.MANDATORY,
    )
    fun create(
        userId: UUID,
        now: Instant,
        clientMetadata: ClientMetadata,
    ): IssuedRefreshToken {

        val issued =
            opaqueTokenService.issue()

        val session =
            RefreshSession(
                userId = userId,
                tokenHash = issued.hash,
                createdAt = now,
                expiresAt =
                    now.plus(
                        refreshTokenTtl
                    ),
                ipAddress =
                    clientMetadata
                        .ipAddress
                        ?.take(64),
                userAgent =
                    clientMetadata
                        .userAgent
                        ?.take(512),
            )

        val saved =
            refreshSessionRepository.save(
                session
            )

        return IssuedRefreshToken(
            token = issued.raw,
            sessionId = saved.id,
            expiresAt = saved.expiresAt,
        )
    }


    @Transactional(
        propagation = Propagation.MANDATORY,
        noRollbackFor = [
            RefreshTokenReuseDetectedException::class,
        ],
    )
    fun rotate(
        rawToken: String,
        now: Instant,
        clientMetadata: ClientMetadata,
    ): RotatedRefreshToken {

        val tokenHash =
            opaqueTokenService.hash(
                rawToken
            )

        /*
         * Lần đọc đầu chỉ lấy scalar userId.
         * Không load entity trước advisory lock để tránh
         * JPA first-level cache giữ state cũ khi phải chờ lock.
         */
        val userId =
            refreshSessionRepository
                .findUserIdByTokenHash(
                    tokenHash
                )
                ?: throw InvalidRefreshTokenException()

        advisoryLockService
            .lockUserSessions(
                userId
            )

        val current =
            refreshSessionRepository
                .findByTokenHash(
                    tokenHash
                )
                ?: throw InvalidRefreshTokenException()

        if (current.revokedAt != null) {

            /*
             * Token đã từng rotate mà bị dùng lại:
             * coi như refresh-token theft/reuse.
             * Revoke mọi active refresh session của account.
             *
             * AuthService.refresh() cấu hình noRollbackFor cho
             * RefreshTokenReuseDetectedException để việc revoke
             * này vẫn COMMIT dù HTTP response là 401.
             */
            if (current.replacedBySessionId != null) {

                refreshSessionRepository
                    .revokeAllActiveByUserId(
                        userId = current.userId,
                        now = now,
                    )

                throw RefreshTokenReuseDetectedException()
            }

            throw InvalidRefreshTokenException()
        }

        if (!current.expiresAt.isAfter(now)) {
            throw InvalidRefreshTokenException()
        }

        val replacement =
            create(
                userId = current.userId,
                now = now,
                clientMetadata =
                    clientMetadata,
            )

        current.revokedAt =
            now

        current.replacedBySessionId =
            replacement.sessionId

        refreshSessionRepository.save(
            current
        )

        return RotatedRefreshToken(
            userId = current.userId,
            token = replacement.token,
            expiresAt = replacement.expiresAt,
        )
    }


    @Transactional(
        propagation = Propagation.MANDATORY,
    )
    fun revoke(
        rawToken: String,
        now: Instant,
    ) {

        val tokenHash =
            opaqueTokenService.hash(
                rawToken
            )

        val userId =
            refreshSessionRepository
                .findUserIdByTokenHash(
                    tokenHash
                )
                ?: return

        advisoryLockService
            .lockUserSessions(
                userId
            )

        val session =
            refreshSessionRepository
                .findByTokenHash(
                    tokenHash
                )
                ?: return

        if (session.revokedAt == null) {

            session.revokedAt =
                now

            refreshSessionRepository.save(
                session
            )
        }
    }
}


data class IssuedRefreshToken(
    val token: String,
    val sessionId: UUID,
    val expiresAt: Instant,
)


data class RotatedRefreshToken(
    val userId: UUID,
    val token: String,
    val expiresAt: Instant,
)
