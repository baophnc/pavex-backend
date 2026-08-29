package com.pavex.shipment.service

import com.pavex.shipment.entity.DeliveryAttempt
import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.DeliveryFailureReason
import com.pavex.shipment.entity.DeliveryProofMethod
import com.pavex.shipment.entity.Parcel
import com.pavex.shipment.entity.Shipment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DeliveryAttemptDomainTest {
    @Test
    fun `delivered requires proof recipient and evidence`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeliveryAttempt.create(
                shipment = Shipment(), parcel = Parcel(), actorUserId = UUID.randomUUID(), attemptNumber = 1,
                idempotencyKey = "attempt-key-1", requestFingerprint = "A".repeat(64),
                outcome = DeliveryAttemptOutcome.DELIVERED, failureReason = null, proofMethod = null,
                recipientName = null, evidenceReference = null, note = null, latitude = null, longitude = null,
                occurredAt = Instant.now(), createdAt = Instant.now(),
            )
        }
    }

    @Test
    fun `failed attempt requires reason and no proof fields`() {
        val attempt = DeliveryAttempt.create(
            shipment = Shipment(), parcel = Parcel(), actorUserId = UUID.randomUUID(), attemptNumber = 2,
            idempotencyKey = "attempt-key-2", requestFingerprint = "B".repeat(64),
            outcome = DeliveryAttemptOutcome.FAILED, failureReason = DeliveryFailureReason.RECIPIENT_UNAVAILABLE,
            proofMethod = null, recipientName = null, evidenceReference = null, note = "No answer",
            latitude = null, longitude = null, occurredAt = Instant.now(), createdAt = Instant.now(),
        )
        assertEquals(DeliveryAttemptOutcome.FAILED, attempt.outcome)
    }

    @Test
    fun `successful attempt accepts proof`() {
        val attempt = DeliveryAttempt.create(
            shipment = Shipment(), parcel = Parcel(), actorUserId = UUID.randomUUID(), attemptNumber = 1,
            idempotencyKey = "attempt-key-3", requestFingerprint = "C".repeat(64),
            outcome = DeliveryAttemptOutcome.DELIVERED, failureReason = null, proofMethod = DeliveryProofMethod.PHOTO,
            recipientName = "Receiver", evidenceReference = "media:proof-123", note = null,
            latitude = null, longitude = null, occurredAt = Instant.now(), createdAt = Instant.now(),
        )
        assertEquals(DeliveryProofMethod.PHOTO, attempt.proofMethod)
    }
}
