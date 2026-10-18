package com.pavex.outbox

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Settings of a lease-based outbox (rows are leased with `lock_id`/`locked_until`, sent outside the
 * claiming transaction, then marked only if the lease is still ours).
 */
data class OutboxLeaseSettings(
    val batchSize: Int,
    val lockDuration: Duration,
    val baseRetryDelay: Duration,
    val maxRetryDelay: Duration,
    val maxAttempts: Int,
) {
    val backoff: RetryBackoff get() = RetryBackoff(baseRetryDelay, maxRetryDelay)

    /** Checked on every batch, so a bad setting fails each dispatch cycle rather than the startup. */
    fun validate() {
        require(batchSize in 1..500)
        require(!lockDuration.isZero && !lockDuration.isNegative)
        require(!baseRetryDelay.isZero && !baseRetryDelay.isNegative)
        require(maxRetryDelay >= baseRetryDelay)
        require(maxAttempts in 1..100)
    }
}

/**
 * Leases due outbox rows to one dispatcher: [selectDue] picks them `FOR UPDATE SKIP LOCKED`, then
 * [lease] stamps each with this dispatcher's lock and counts the attempt. Call it inside a
 * transaction so the row locks hold until every row is leased.
 *
 * - [selectDue] gets `(now, now, batchSize)`: due, not leased (`locked_until` empty or past), and
 *   whatever ordering rule the service has.
 * - [lease] gets `(lockId, lockedUntil, id)` and must update exactly that one still-open row.
 */
class JdbcOutboxLease(
    private val jdbc: JdbcTemplate,
    private val label: String,
    private val selectDue: String,
    private val lease: String,
) {
    fun <M> claim(
        lockId: UUID,
        now: Instant,
        batchSize: Int,
        lockDuration: Duration,
        rowMapper: RowMapper<M>,
        idOf: (M) -> UUID,
    ): List<M> {
        val candidates = jdbc.query(selectDue, rowMapper, Timestamp.from(now), Timestamp.from(now), batchSize)
        if (candidates.isEmpty()) return emptyList()

        val lockedUntil = Timestamp.from(now.plus(lockDuration))
        candidates.forEach { candidate ->
            val id = idOf(candidate)
            check(jdbc.update(lease, lockId, lockedUntil, id) == 1) { "$label $id could not be locked." }
        }
        return candidates
    }
}
