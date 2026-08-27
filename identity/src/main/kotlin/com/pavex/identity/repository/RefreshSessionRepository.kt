package com.pavex.identity.repository

import com.pavex.identity.entity.RefreshSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface RefreshSessionRepository :
    JpaRepository<RefreshSession, UUID> {

    fun findByTokenHash(
        tokenHash: String,
    ): RefreshSession?
    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true,
    )
    @Query(
        """
    update RefreshSession s
    set s.revokedAt = :now
    where s.userId = :userId
      and s.revokedAt is null
      and s.expiresAt > :now
    """
    )
    fun revokeAllActiveByUserId(
        userId: UUID,
        now: Instant,
    ): Int
    fun findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAtDesc(
        userId: UUID,
        expiresAt: Instant,
    ): List<RefreshSession>

    fun findByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
        id: UUID,
        userId: UUID,
        expiresAt: Instant,
    ): RefreshSession?
}