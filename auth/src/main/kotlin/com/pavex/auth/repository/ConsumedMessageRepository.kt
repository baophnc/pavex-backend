package com.pavex.auth.repository

import com.pavex.auth.entity.ConsumedMessage
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
            DELETE FROM auth.consumed_message
            WHERE processed_at < :cutoff
        """,
        nativeQuery = true,
    )
    fun deleteProcessedBefore(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
