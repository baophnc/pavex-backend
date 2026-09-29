package com.pavex.shipment.dispatch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ShipmentOutboxProcessorTest {
    private val calls = mutableListOf<String>()

    @Test
    fun `confirmed event is marked published`() {
        val result = processor(attempts = 1, publishFails = false).processBatch()

        assertEquals(1, result.published)
        assertEquals(listOf("markPublished"), calls)
    }

    @Test
    fun `failed publish is retried until the attempt limit, then dead-lettered`() {
        val retried = processor(attempts = 2, publishFails = true).processBatch()
        assertEquals(1, retried.failed)
        assertEquals(listOf("markRetry"), calls)

        calls.clear()
        val deadLettered = processor(attempts = 8, publishFails = true).processBatch()
        assertEquals(1, deadLettered.deadLettered)
        assertEquals(listOf("markDeadLettered"), calls)
    }

    private fun processor(attempts: Int, publishFails: Boolean): ShipmentOutboxProcessor {
        val event = ShipmentOutboxDelivery(
            id = UUID.randomUUID(),
            aggregateId = UUID.randomUUID(),
            aggregateCode = "PAVEX2609250123456789",
            eventType = "shipment.closed",
            eventVersion = 1,
            payload = "{}",
            occurredAt = Instant.parse("2026-09-25T03:00:00Z"),
            attempts = attempts,
        )
        val store = mock(ShipmentOutboxStore::class.java) { invocation ->
            when (val name = invocation.method.name) {
                "claimBatch" -> listOf(event)
                "markPublished", "markRetry", "markDeadLettered" -> true.also { calls += name }
                else -> null
            }
        }
        val publisher = mock(RabbitShipmentEventPublisher::class.java) { invocation ->
            if (publishFails && invocation.method.name == "publish") error("broker unavailable")
            null
        }
        return ShipmentOutboxProcessor(
            store = store,
            publisher = publisher,
            batchSize = 10,
            lockDuration = Duration.ofSeconds(30),
            baseRetryDelay = Duration.ofSeconds(5),
            maxRetryDelay = Duration.ofMinutes(5),
            maxAttempts = 8,
        )
    }
}
