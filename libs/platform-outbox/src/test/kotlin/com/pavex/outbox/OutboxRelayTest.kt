package com.pavex.outbox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class OutboxRelayTest {
    private data class Row(val id: Int, val attempt: Int)

    private class RecordingStore(
        private val published: Boolean = true,
        private val deadLettered: Boolean = true,
    ) : OutboxRelayStore<Row> {
        val calls = mutableListOf<String>()

        override fun markPublished(message: Row, at: Instant) = published.also { calls += "published ${message.id} $at" }
        override fun markRetry(message: Row, nextAttemptAt: Instant, error: String) =
            true.also { calls += "retry ${message.id} $nextAttemptAt $error" }
        override fun markDeadLettered(message: Row, at: Instant, error: String) =
            deadLettered.also { calls += "dead ${message.id} $at $error" }
    }

    private val now = Instant.parse("2026-10-18T08:00:00Z")
    private val relay = OutboxRelay<Row>(
        label = "Test outbox row",
        log = LoggerFactory.getLogger(OutboxRelayTest::class.java),
        backoff = RetryBackoff(Duration.ofSeconds(5), Duration.ofMinutes(5)),
        maxAttempts = 3,
        idOf = Row::id,
        attemptOf = Row::attempt,
    )

    @Test
    fun `sent rows are marked published, failed ones retried with backoff, the last attempt dead-lettered`() {
        val store = RecordingStore()
        val result = relay.relay(listOf(Row(1, 1), Row(2, 2), Row(3, 3)), store, { now }) {
            if (it.id != 1) throw IllegalStateException("broker unavailable")
        }

        assertEquals(OutboxBatchResult(claimed = 3, published = 1, failed = 2, deadLettered = 1), result)
        assertEquals(
            listOf(
                "published 1 $now",
                "retry 2 ${now.plusSeconds(10)} IllegalStateException: broker unavailable",
                "dead 3 $now IllegalStateException: broker unavailable",
            ),
            store.calls,
        )
    }

    @Test
    fun `a row whose lease was lost counts as failed, also when dead-lettering it`() {
        val store = RecordingStore(published = false, deadLettered = false)

        assertEquals(OutboxOutcome.FAILED, relay.deliver(Row(1, 1), store, { now }) {})
        assertEquals(OutboxOutcome.FAILED, relay.deliver(Row(2, 3), store, { now }) { error("down") })
        assertEquals(OutboxBatchResult(1, 0, 1, 0), relay.relay(listOf(Row(3, 3)), store, { now }) { error("down") })
    }

    @Test
    fun `without an attempt limit rows are retried forever, with the service's own error text`() {
        val forever = OutboxRelay<Row>(
            label = "Test outbox row",
            log = LoggerFactory.getLogger(OutboxRelayTest::class.java),
            backoff = RetryBackoff(Duration.ofSeconds(5), Duration.ofSeconds(1800), maxDoublings = 8),
            maxAttempts = null,
            idOf = Row::id,
            attemptOf = Row::attempt,
            describeError = { it.message ?: it.javaClass.simpleName },
        )
        val store = RecordingStore()

        assertEquals(OutboxOutcome.FAILED, forever.deliver(Row(1, 500), store, { now }) { error("identity down") })
        assertEquals(listOf("retry 1 ${now.plusSeconds(1280)} identity down"), store.calls)
    }

    @Test
    fun `backoff doubles from the base delay, up to the cap`() {
        val shipment = RetryBackoff(Duration.ofSeconds(5), Duration.ofMinutes(5))
        assertEquals(listOf(5L, 10L, 20L, 40L, 80L, 160L, 300L, 300L), (1..8).map { shipment.delayAfter(it).seconds })
        assertEquals(Duration.ofSeconds(5), shipment.delayAfter(0))
        assertEquals(Duration.ofMinutes(5), shipment.delayAfter(Int.MAX_VALUE))

        val partners = RetryBackoff(Duration.ofSeconds(5), Duration.ofSeconds(1800), maxDoublings = 8)
        assertEquals(Duration.ofSeconds(10), partners.delayAfter(2))
        assertEquals(Duration.ofSeconds(1280), partners.delayAfter(9))
        assertEquals(Duration.ofSeconds(1280), partners.delayAfter(50))

        // Auth: 2^attempt seconds, attempt counted 1..8, at most 300 s.
        val auth = RetryBackoff(Duration.ofSeconds(2), Duration.ofSeconds(300), maxDoublings = 7)
        assertEquals(listOf(2L, 4L, 8L, 16L, 32L, 64L, 128L, 256L, 256L), (1..9).map { auth.delayAfter(it).seconds })

        assertEquals(Duration.ofMillis(750), RetryBackoff(Duration.ofMillis(750), Duration.ofSeconds(1)).delayAfter(1))
        assertEquals(Duration.ofSeconds(1), RetryBackoff(Duration.ofMillis(750), Duration.ofSeconds(1)).delayAfter(2))
        assertThrows(IllegalArgumentException::class.java) { RetryBackoff(Duration.ofSeconds(1), Duration.ofSeconds(2), maxDoublings = 63) }
    }

    @Test
    fun `error text names the exception and fits the column`() {
        assertEquals("IllegalStateException: down", OutboxErrors.describe(IllegalStateException("down")))
        assertEquals("RuntimeException: unknown", OutboxErrors.describe(RuntimeException()))
        assertEquals(2000, OutboxErrors.describe(RuntimeException("x".repeat(5000))).length)
    }

    @Test
    fun `lease settings reject values the dispatchers cannot work with`() {
        val valid = OutboxLeaseSettings(50, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMinutes(5), 8)
        valid.validate()
        assertEquals(RetryBackoff(Duration.ofSeconds(5), Duration.ofMinutes(5)), valid.backoff)
        listOf(
            valid.copy(batchSize = 0),
            valid.copy(batchSize = 501),
            valid.copy(lockDuration = Duration.ZERO),
            valid.copy(baseRetryDelay = Duration.ofSeconds(-1)),
            valid.copy(maxRetryDelay = Duration.ofSeconds(1)),
            valid.copy(maxAttempts = 101),
        ).forEach { assertThrows(IllegalArgumentException::class.java, it::validate, it.toString()) }
    }
}
