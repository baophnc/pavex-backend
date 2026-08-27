package com.pavex.identity.repository

import com.pavex.identity.entity.ConsumedMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ConsumedMessageRepository :
    JpaRepository<ConsumedMessage, UUID> {

    fun existsByMessageIdAndConsumer(
        messageId: UUID,
        consumer: String,
    ): Boolean

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true,
    )
    @Query(
        value = """
            DELETE FROM identity.consumed_message
            WHERE processed_at < :cutoff
        """,
        nativeQuery = true,
    )
    fun deleteProcessedBefore(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
