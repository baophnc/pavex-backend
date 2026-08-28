package com.pavex.auth.repository

import com.pavex.auth.entity.EmailVerificationToken
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
        update EmailVerificationToken token
        set token.usedAt = :now
        where token.id = :id
          and token.usedAt is null
          and token.expiresAt > :now
        """
    )
    fun consumeIfValid(
        @Param("id")
        id: UUID,

        @Param("now")
        now: Instant,
    ): Int


    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true,
    )
    @Query(
        """
        delete from EmailVerificationToken token
        where token.expiresAt < :cutoff
           or (
                token.usedAt is not null
                and token.usedAt < :cutoff
           )
        """
    )
    fun deleteStale(
        @Param("cutoff")
        cutoff: Instant,
    ): Int
}
