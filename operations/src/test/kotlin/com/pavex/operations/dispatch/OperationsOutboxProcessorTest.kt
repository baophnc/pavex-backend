package com.pavex.operations.dispatch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OperationsOutboxProcessorTest {
    @Test
    fun `confirmed message is marked processed and measured`() {
        val delivery = OperationsOutboxDelivery(
            messageId = UUID.randomUUID(),
            eventType = "operations.assignment.assigned",
            aggregateType = "OperationalAssignment",
            aggregateId = UUID.randomUUID(),
            payload = "{}",
            occurredAt = Instant.parse("2026-08-31T08:00:00Z"),
            attemptCount = 0,
        )
        val store = mock(OperationsOutboxStore::class.java) { invocation ->
            when (invocation.method.name) {
                "claimBatch" -> listOf(delivery)
                "markProcessed" -> true
                else -> null
            }
        }
        val publisher = mock(RabbitOperationsEventPublisher::class.java)
        val telemetry = mock(OperationsTelemetry::class.java)

        val processor = OperationsOutboxProcessor(
            store = store,
            publisher = publisher,
            telemetry = telemetry,
            batchSize = 10,
            lockDuration = Duration.ofSeconds(30),
            baseRetryDelay = Duration.ofSeconds(5),
            maxRetryDelay = Duration.ofMinutes(5),
            maxAttempts = 8,
        )

        val result = processor.processBatch()

        assertEquals(1, result.claimed)
        assertEquals(1, result.processed)
        assertEquals(0, result.failed)
    }
}
