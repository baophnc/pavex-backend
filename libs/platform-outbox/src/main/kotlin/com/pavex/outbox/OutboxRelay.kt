package com.pavex.outbox

import org.slf4j.Logger
import java.time.Duration
import java.time.Instant

/**
 * Exponential retry delay: [baseDelay] after the first failed attempt, doubling with each further
 * attempt (at most [maxDoublings] times), never more than [maxDelay]. Millisecond precision.
 */
data class RetryBackoff(
    val baseDelay: Duration,
    val maxDelay: Duration,
    val maxDoublings: Int = 20,
) {
    init {
        require(maxDoublings in 0..40) { "maxDoublings must be between 0 and 40." }
    }

    fun delayAfter(attempt: Int): Duration {
        val doublings = (attempt - 1).coerceIn(0, maxDoublings)
        return Duration.ofMillis((baseDelay.toMillis() * (1L shl doublings)).coerceAtMost(maxDelay.toMillis()))
    }
}

object OutboxErrors {
    /** `IllegalStateException: broker unavailable`, cut to fit a `last_error` column. */
    fun describe(exception: Exception, maxLength: Int = 2000): String =
        "${exception::class.simpleName}: ${exception.message ?: "unknown"}".take(maxLength)
}

/** How a service records the outcome of one delivery on its own outbox row. */
interface OutboxRelayStore<M> {
    /** The broker or receiving service confirmed it. False when the row is no longer ours (lease lost). */
    fun markPublished(message: M, at: Instant): Boolean

    /** It failed and will be tried again at [nextAttemptAt]. */
    fun markRetry(message: M, nextAttemptAt: Instant, error: String): Boolean

    /** It failed on its last allowed attempt and stays aside until someone requeues or discards it. */
    fun markDeadLettered(message: M, at: Instant, error: String): Boolean
}

data class OutboxBatchResult(
    val claimed: Int,
    val published: Int,
    val failed: Int,
    val deadLettered: Int,
)

enum class OutboxOutcome { PUBLISHED, FAILED, DEAD_LETTERED }

/**
 * Sends claimed outbox rows one by one and records each outcome: published, or failed with a
 * retry after [backoff], or dead-lettered once the attempt reaches [maxAttempts] (`null`: retry forever).
 *
 * Claiming the rows, what an attempt number means and how a row is sent stay with the service:
 * [attemptOf] gives the number of this attempt, counting from 1.
 */
class OutboxRelay<M>(
    private val label: String,
    private val log: Logger,
    private val backoff: RetryBackoff,
    private val maxAttempts: Int?,
    private val idOf: (M) -> Any,
    private val attemptOf: (M) -> Int,
    private val describeError: (Exception) -> String = { OutboxErrors.describe(it) },
) {
    /** [clock] is read at each outcome; pass a fixed time to stamp a whole batch with its start. */
    fun relay(
        messages: List<M>,
        store: OutboxRelayStore<M>,
        clock: () -> Instant = Instant::now,
        send: (M) -> Unit,
    ): OutboxBatchResult {
        val outcomes = messages.map { deliver(it, store, clock, send) }
        return OutboxBatchResult(
            claimed = messages.size,
            published = outcomes.count { it == OutboxOutcome.PUBLISHED },
            failed = outcomes.count { it != OutboxOutcome.PUBLISHED },
            deadLettered = outcomes.count { it == OutboxOutcome.DEAD_LETTERED },
        )
    }

    fun deliver(
        message: M,
        store: OutboxRelayStore<M>,
        clock: () -> Instant = Instant::now,
        send: (M) -> Unit,
    ): OutboxOutcome =
        try {
            send(message)
            if (store.markPublished(message, clock())) OutboxOutcome.PUBLISHED else OutboxOutcome.FAILED
        } catch (exception: Exception) {
            failed(message, exception, store, clock())
        }

    private fun failed(message: M, exception: Exception, store: OutboxRelayStore<M>, now: Instant): OutboxOutcome {
        val attempt = attemptOf(message)
        val error = describeError(exception)
        if (maxAttempts != null && attempt >= maxAttempts) {
            val marked = store.markDeadLettered(message, now, error)
            log.error("{} {} dead-lettered after {} attempts.", label, idOf(message), attempt, exception)
            return if (marked) OutboxOutcome.DEAD_LETTERED else OutboxOutcome.FAILED
        }
        val retryAt = now.plus(backoff.delayAfter(attempt))
        store.markRetry(message, retryAt, error)
        log.warn("{} {} failed on attempt {}; retry scheduled for {}.", label, idOf(message), attempt, retryAt)
        return OutboxOutcome.FAILED
    }
}
