package com.pavex.auth.repository

import com.pavex.auth.entity.OutboxMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface OutboxMessageRepository :
    JpaRepository<OutboxMessage, UUID> {

    @Query(
        value = """
            SELECT *
            FROM auth.outbox_message
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY occurred_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockPendingBatch(
        @Param("now") now: Instant,
        @Param("batchSize") batchSize: Int,
    ): List<OutboxMessage>

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true,
    )
    @Query(
        value = """
            DELETE FROM auth.outbox_message
            WHERE status = 'PUBLISHED'
              AND published_at < :cutoff
        """,
        nativeQuery = true,
    )
    fun deletePublishedBefore(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
