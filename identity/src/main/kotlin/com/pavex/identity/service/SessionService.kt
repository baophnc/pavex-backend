package com.pavex.identity.service

import com.pavex.identity.dto.auth.SessionResponse
import com.pavex.identity.exception.ActiveSessionNotFoundException
import com.pavex.identity.repository.RefreshSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SessionService(

    private val refreshSessionRepository:
        RefreshSessionRepository,
) {

    @Transactional(
        readOnly = true,
    )
    fun getSessions(
        userId: UUID,
    ): List<SessionResponse> {

        val now =
            Instant.now()

        return refreshSessionRepository
            .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAtDesc(
                userId = userId,
                expiresAt = now,
            )
            .map { session ->
                SessionResponse(
                    sessionId =
                        session.id,

                    ipAddress =
                        session.ipAddress,

                    userAgent =
                        session.userAgent,

                    expiresAt =
                        session.expiresAt,
                )
            }
    }


    @Transactional
    fun revokeSession(
        userId: UUID,
        sessionId: UUID,
    ) {
        val now =
            Instant.now()

        val session =
            refreshSessionRepository
                .findByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                    id = sessionId,
                    userId = userId,
                    expiresAt = now,
                )
                ?: throw ActiveSessionNotFoundException()

        session.revokedAt =
            now

        refreshSessionRepository.save(
            session
        )
    }


    @Transactional
    fun logoutAll(
        userId: UUID,
    ) {
        refreshSessionRepository
            .revokeAllActiveByUserId(
                userId = userId,
                now = Instant.now(),
            )
    }
}
