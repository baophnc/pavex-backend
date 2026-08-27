package com.pavex.identity.repository

import com.pavex.identity.entity.InvitationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface InvitationTokenRepository :
    JpaRepository<InvitationToken, UUID> {

    fun findByTokenHash(
        tokenHash: String,
    ): InvitationToken?


    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true,
    )
    @Query(
        """
        update InvitationToken t
        set t.usedAt = :now
        where t.id = :id
          and t.usedAt is null
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
        update InvitationToken t
        set t.usedAt = :now
        where t.userId = :userId
          and t.usedAt is null
        """
    )
    fun invalidateUnusedByUserId(
        @Param("userId")
        userId: UUID,

        @Param("now")
        now: Instant,
    ): Int
}
