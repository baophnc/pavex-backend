package com.pavex.identity.service

import com.pavex.identity.entity.RefreshSession
import com.pavex.identity.exception.InvalidRefreshTokenException
import com.pavex.identity.repository.RefreshSessionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class RefreshTokenService(

    private val refreshSessionRepository:
    RefreshSessionRepository,

    @Value("\${security.jwt.refresh-token-ttl}")
    private val refreshTokenTtl: Duration,
) {

    private val secureRandom =
        SecureRandom()

    fun create(
        userId: UUID,
        now: Instant,
    ): IssuedRefreshToken {

        val rawToken =
            generateToken()

        val session = RefreshSession(
            userId = userId,
            tokenHash = hashToken(rawToken),
            createdAt = now,
            expiresAt = now.plus(
                refreshTokenTtl
            ),
        )

        val saved =
            refreshSessionRepository.save(
                session
            )

        return IssuedRefreshToken(
            token = rawToken,
            sessionId = saved.id,
            expiresAt = saved.expiresAt,
        )
    }

    fun rotate(
        rawToken: String,
        now: Instant,
    ): RotatedRefreshToken {

        val current =
            refreshSessionRepository
                .findByTokenHash(
                    hashToken(rawToken)
                )
                ?: throw InvalidRefreshTokenException()

        if (
            current.revokedAt != null ||
            !current.expiresAt.isAfter(now)
        ) {
            throw InvalidRefreshTokenException()
        }

        val replacement =
            create(
                current.userId,
                now,
            )

        current.revokedAt = now

        current.replacedBySessionId =
            replacement.sessionId

        refreshSessionRepository.save(
            current
        )

        return RotatedRefreshToken(
            userId = current.userId,
            token = replacement.token,
            expiresAt =
                replacement.expiresAt,
        )
    }

    fun revoke(
        rawToken: String,
        now: Instant,
    ) {

        val session =
            refreshSessionRepository
                .findByTokenHash(
                    hashToken(rawToken)
                )
                ?: return

        if (session.revokedAt == null) {
            session.revokedAt = now

            refreshSessionRepository.save(
                session
            )
        }
    }

    private fun generateToken(): String {

        val bytes =
            ByteArray(32)

        secureRandom.nextBytes(bytes)

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    private fun hashToken(
        token: String,
    ): String {

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    token.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

        return digest.joinToString("") {
            "%02x".format(
                it.toInt() and 0xff
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