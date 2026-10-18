package com.pavex.operations.dispatch

import com.pavex.outbox.JdbcOutboxLease
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
class OperationsOutboxStore(
    private val jdbc: JdbcTemplate,
) {
    private val lease = JdbcOutboxLease(
        jdbc,
        label = "Outbox message",
        selectDue = """
        SELECT candidate.id, candidate.event_type, candidate.aggregate_type, candidate.aggregate_id,
               candidate.payload::text, candidate.occurred_at, candidate.attempt_count
        FROM operations.outbox_messages candidate
        WHERE candidate.processed_at IS NULL
          AND candidate.dead_lettered_at IS NULL
          AND candidate.discarded_at IS NULL
          AND candidate.available_at <= ?
          AND (candidate.locked_until IS NULL OR candidate.locked_until <= ?)
          AND NOT EXISTS (
              SELECT 1 FROM operations.outbox_messages predecessor
              WHERE predecessor.aggregate_type = candidate.aggregate_type
                AND predecessor.aggregate_id = candidate.aggregate_id
                AND predecessor.processed_at IS NULL
                AND predecessor.discarded_at IS NULL
                AND (
                    predecessor.occurred_at < candidate.occurred_at OR
                    (predecessor.occurred_at = candidate.occurred_at AND predecessor.id < candidate.id)
                )
          )
        ORDER BY candidate.occurred_at, candidate.id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """.trimIndent(),
        lease = """
        UPDATE operations.outbox_messages
        SET lock_id = ?, locked_until = ?, attempt_count = attempt_count + 1
        WHERE id = ?
          AND processed_at IS NULL
          AND dead_lettered_at IS NULL
          AND discarded_at IS NULL
        """.trimIndent(),
    )

    @Transactional
    fun claimBatch(lockId: UUID, now: Instant, batchSize: Int, lockDuration: Duration): List<OperationsOutboxDelivery> =
        lease.claim(lockId, now, batchSize, lockDuration, { rs, _ -> delivery(rs) }, OperationsOutboxDelivery::messageId)
            .map { it.copy(attemptCount = it.attemptCount + 1) }

    fun markProcessed(messageId: UUID, lockId: UUID, processedAt: Instant): Boolean = jdbc.update(
        """
        UPDATE operations.outbox_messages
        SET processed_at = ?, last_error = NULL, lock_id = NULL, locked_until = NULL
        WHERE id = ? AND processed_at IS NULL AND lock_id = ?
        """.trimIndent(),
        Timestamp.from(processedAt), messageId, lockId,
    ) == 1

    fun markFailed(messageId: UUID, lockId: UUID, availableAt: Instant, error: String): Boolean = jdbc.update(
        """
        UPDATE operations.outbox_messages
        SET available_at = ?, last_error = ?, lock_id = NULL, locked_until = NULL
        WHERE id = ? AND processed_at IS NULL AND lock_id = ?
        """.trimIndent(),
        Timestamp.from(availableAt), error, messageId, lockId,
    ) == 1

    fun markDeadLettered(messageId: UUID, lockId: UUID, at: Instant, error: String): Boolean = jdbc.update(
        """
        UPDATE operations.outbox_messages
        SET dead_lettered_at = ?, last_error = ?, lock_id = NULL, locked_until = NULL
        WHERE id = ? AND processed_at IS NULL AND lock_id = ?
        """.trimIndent(),
        Timestamp.from(at), error, messageId, lockId,
    ) == 1

    private fun delivery(rs: ResultSet): OperationsOutboxDelivery = OperationsOutboxDelivery(
        messageId = rs.getObject("id", UUID::class.java),
        eventType = rs.getString("event_type"),
        aggregateType = rs.getString("aggregate_type"),
        aggregateId = rs.getObject("aggregate_id", UUID::class.java),
        payload = rs.getString("payload"),
        occurredAt = rs.getTimestamp("occurred_at").toInstant(),
        attemptCount = rs.getInt("attempt_count"),
    )
}
