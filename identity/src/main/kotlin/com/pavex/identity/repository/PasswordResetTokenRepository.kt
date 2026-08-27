package com.pavex.identity.repository

import com.pavex.identity.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
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
        id: UUID,
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
        userId: UUID,
        now: Instant,
    ): Int
}
