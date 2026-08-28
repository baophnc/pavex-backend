package com.pavex.auth.repository

import com.pavex.auth.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PasswordResetTokenRepository :
    JpaRepository<PasswordResetToken, UUID> {

    fun findByTokenHash(
        tokenHash: String,
    ): PasswordResetToken?


    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true,
    )
    @Query(
        """
        update PasswordResetToken t
        set t.usedAt = :now
        where t.id = :id
          and t.usedAt is null
          and t.revokedAt is null
          and t.expiresAt > :now
        """
    )
    fun consumeIfValid(
        @Param("id")
        id: UUID,

        @Param("now")
        now: Instant,
    ): Int


    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true,
    )
    @Query(
        """
        update PasswordResetToken t
        set t.revokedAt = :now
        where t.userId = :userId
          and t.usedAt is null
          and t.revokedAt is null
        """
    )
    fun revokeUnusedByUserId(
        @Param("userId")
        userId: UUID,

        @Param("now")
        now: Instant,
    ): Int


    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true,
    )
    @Query(
        """
        delete from PasswordResetToken t
        where t.expiresAt < :cutoff
           or (
                t.usedAt is not null
                and t.usedAt < :cutoff
           )
           or (
                t.revokedAt is not null
                and t.revokedAt < :cutoff
           )
        """
    )
    fun deleteStale(
        @Param("cutoff")
        cutoff: Instant,
    ): Int
}
