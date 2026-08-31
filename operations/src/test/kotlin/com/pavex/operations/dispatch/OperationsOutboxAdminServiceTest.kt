package com.pavex.operations.dispatch

import com.pavex.operations.entity.OperationsOutboxMessage
import com.pavex.operations.exception.InvalidOperationsRequestException
import com.pavex.operations.exception.OperationsConflictException
import com.pavex.operations.repository.OperationsOutboxRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class OperationsOutboxAdminServiceTest {
    private val repository = mock(OperationsOutboxRepository::class.java)
    private val telemetry = mock(OperationsTelemetry::class.java)
    private val service = OperationsOutboxAdminService(repository, telemetry)

    @Test
    fun `retry requeues active dead letter and resets delivery state`() {
        val actor = UUID.randomUUID()
        val message = deadLetter()
        `when`(repository.findByIdForUpdate(message.id)).thenReturn(message)
        `when`(repository.saveAndFlush(message)).thenReturn(message)

        service.retry(actor, message.id)

        assertNull(message.deadLetteredAt)
        assertEquals(0, message.attemptCount)
        assertNull(message.lockId)
        assertNull(message.lockedUntil)
        verify(telemetry).recordManualRetry()
    }

    @Test
    fun `discard requires a bounded reason and records actor`() {
        val actor = UUID.randomUUID()
        val message = deadLetter()
        `when`(repository.findByIdForUpdate(message.id)).thenReturn(message)
        `when`(repository.saveAndFlush(message)).thenReturn(message)

        service.discard(actor, message.id, "invalid payload from legacy producer")

        assertEquals(actor, message.discardedByUserId)
        assertEquals("invalid payload from legacy producer", message.discardReason)
        verify(telemetry).recordManualDiscard()
        assertThrows(InvalidOperationsRequestException::class.java) {
            service.discard(actor, UUID.randomUUID(), " ")
        }
    }

    @Test
    fun `processed message cannot be retried as dead letter`() {
        val message = deadLetter().apply { processedAt = Instant.now() }
        `when`(repository.findByIdForUpdate(message.id)).thenReturn(message)
        assertThrows(OperationsConflictException::class.java) {
            service.retry(UUID.randomUUID(), message.id)
        }
    }

    private fun deadLetter() = OperationsOutboxMessage(
        eventType = "operations.assignment.rejected",
        aggregateType = "OperationalAssignment",
        aggregateId = UUID.randomUUID(),
        payload = "{}",
        occurredAt = Instant.parse("2026-08-31T08:00:00Z"),
        availableAt = Instant.parse("2026-08-31T08:00:00Z"),
        deadLetteredAt = Instant.parse("2026-08-31T08:05:00Z"),
        attemptCount = 8,
        lastError = "broker unavailable",
        lockId = UUID.randomUUID(),
        lockedUntil = Instant.parse("2026-08-31T08:10:00Z"),
    )
}
