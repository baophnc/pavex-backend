package com.pavex.shipment.dispatch

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ShipmentOutboxDelivery(
    val id: UUID,
    val aggregateId: UUID,
    val aggregateCode: String,
    val eventType: String,
    val eventVersion: Int,
    val payload: String,
    val occurredAt: Instant,
    val attempts: Int,
)

/**
 * Leases PENDING outbox rows to one dispatcher at a time. A shipment's events go out in order:
 * a row waits while an earlier PENDING row of the same shipment exists. Dead-lettered (FAILED)
 * rows do not block later ones.
 */
@Repository
class ShipmentOutboxStore(
    private val jdbc: JdbcTemplate,
) {
    @Transactional
    fun claimBatch(lockId: UUID, now: Instant, batchSize: Int, lockDuration: Duration): List<ShipmentOutboxDelivery> {
        val candidates = jdbc.query(
            """
            SELECT candidate.id, candidate.aggregate_id, candidate.aggregate_code, candidate.event_type,
                   candidate.event_version, candidate.payload_json, candidate.occurred_at, candidate.attempts
            FROM shipments.outbox_events candidate
            WHERE candidate.status = 'PENDING'
              AND candidate.next_attempt_at <= ?
              AND (candidate.locked_until IS NULL OR candidate.locked_until <= ?)
              AND NOT EXISTS (
                  SELECT 1 FROM shipments.outbox_events predecessor
                  WHERE predecessor.aggregate_id = candidate.aggregate_id
                    AND predecessor.status = 'PENDING'
                    AND (
                        predecessor.occurred_at < candidate.occurred_at OR
                        (predecessor.occurred_at = candidate.occurred_at AND predecessor.created_at < candidate.created_at) OR
                        (predecessor.occurred_at = candidate.occurred_at AND predecessor.created_at = candidate.created_at AND predecessor.id < candidate.id)
                    )
              )
            ORDER BY candidate.occurred_at, candidate.created_at, candidate.id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            { rs, _ -> delivery(rs) },
            Timestamp.from(now),
            Timestamp.from(now),
            batchSize,
        )
        if (candidates.isEmpty()) return emptyList()

        val lockedUntil = now.plus(lockDuration)
        candidates.forEach { candidate ->
            val updated = jdbc.update(
                """
                UPDATE shipments.outbox_events
                SET lock_id = ?, locked_until = ?, attempts = attempts + 1
                WHERE id = ? AND status = 'PENDING'
                """.trimIndent(),
                lockId,
                Timestamp.from(lockedUntil),
                candidate.id,
            )
            check(updated == 1) { "Shipment outbox event ${candidate.id} could not be locked." }
        }
        return candidates.map { it.copy(attempts = it.attempts + 1) }
    }

    fun markPublished(id: UUID, lockId: UUID, publishedAt: Instant): Boolean = jdbc.update(
        """
        UPDATE shipments.outbox_events
        SET status = 'PUBLISHED', published_at = ?, last_error = NULL, lock_id = NULL, locked_until = NULL
        WHERE id = ? AND status = 'PENDING' AND lock_id = ?
        """.trimIndent(),
        Timestamp.from(publishedAt), id, lockId,
    ) == 1

    fun markRetry(id: UUID, lockId: UUID, nextAttemptAt: Instant, error: String): Boolean = jdbc.update(
        """
        UPDATE shipments.outbox_events
        SET next_attempt_at = ?, last_error = ?, lock_id = NULL, locked_until = NULL
        WHERE id = ? AND status = 'PENDING' AND lock_id = ?
        """.trimIndent(),
        Timestamp.from(nextAttemptAt), error, id, lockId,
    ) == 1

    fun markDeadLettered(id: UUID, lockId: UUID, error: String): Boolean = jdbc.update(
        """
        UPDATE shipments.outbox_events
        SET status = 'FAILED', last_error = ?, lock_id = NULL, locked_until = NULL
        WHERE id = ? AND status = 'PENDING' AND lock_id = ?
        """.trimIndent(),
        error, id, lockId,
    ) == 1

    private fun delivery(rs: ResultSet) = ShipmentOutboxDelivery(
        id = rs.getObject("id", UUID::class.java),
        aggregateId = rs.getObject("aggregate_id", UUID::class.java),
        aggregateCode = rs.getString("aggregate_code"),
        eventType = rs.getString("event_type"),
        eventVersion = rs.getInt("event_version"),
        payload = rs.getString("payload_json"),
        occurredAt = rs.getTimestamp("occurred_at").toInstant(),
        attempts = rs.getInt("attempts"),
    )
}
