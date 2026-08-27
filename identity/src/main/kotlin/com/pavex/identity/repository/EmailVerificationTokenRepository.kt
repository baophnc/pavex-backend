package com.pavex.identity.repository

import com.pavex.identity.entity.EmailVerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface EmailVerificationTokenRepository :
    JpaRepository<EmailVerificationToken, UUID> {

    fun findByTokenHash(
        tokenHash: String,
    ): EmailVerificationToken?

    fun existsByUserIdAndCreatedAtGreaterThanEqual(
        userId: UUID,
        createdAt: Instant,
    ): Boolean

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true,
    )
    @Query(
        """
        UPDATE EmailVerificationToken token
        SET token.usedAt = :now
        WHERE token.id = :id
          AND token.usedAt IS NULL
          AND token.expiresAt > :now
        """
    )
    fun consumeIfValid(
        @Param("id")
        id: UUID,

        @Param("now")
        now: Instant,
    ): Int
}
