package com.pavex.operations.dispatch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OperationsOutboxProcessorTest {
    private val store = mock(OperationsOutboxStore::class.java)
    private val publisher = mock(RabbitOperationsEventPublisher::class.java)
    private val telemetry = mock(OperationsTelemetry::class.java)

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
        `when`(
            store.claimBatch(
                any(UUID::class.java),
                any(Instant::class.java),
                org.mockito.ArgumentMatchers.eq(10),
                any(Duration::class.java),
            ),
        ).thenReturn(listOf(delivery))
        `when`(
            store.markProcessed(
                org.mockito.ArgumentMatchers.eq(delivery.messageId),
                any(UUID::class.java),
                any(Instant::class.java),
            ),
        ).thenReturn(true)

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
        verify(publisher).publish(org.mockito.ArgumentMatchers.any(OperationsIntegrationEvent::class.java))
        verify(telemetry).recordOutboxBatch(result)
    }
}
