package com.pavex.auth.repository

import com.pavex.auth.entity.RefreshSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface RefreshSessionRepository :
    JpaRepository<RefreshSession, UUID> {

    fun findByTokenHash(
        tokenHash: String,
    ): RefreshSession?


    @Query(
        """
        select s.userId
        from RefreshSession s
        where s.tokenHash = :tokenHash
        """
    )
    fun findUserIdByTokenHash(
        @Param("tokenHash")
        tokenHash: String,
    ): UUID?


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
        @Param("userId")
        userId: UUID,

        @Param("now")
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


    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true,
    )
    @Query(
        """
        delete from RefreshSession s
        where s.expiresAt < :cutoff
           or (
                s.revokedAt is not null
                and s.revokedAt < :cutoff
           )
        """
    )
    fun deleteStale(
        @Param("cutoff")
        cutoff: Instant,
    ): Int
}
