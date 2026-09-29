package com.pavex.billing.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CashCustodyTest {
    private val now = Instant.parse("2026-09-25T03:00:00Z")
    private val courier = UUID.randomUUID()
    private val hub = UUID.randomUUID()

    @Test
    fun `courier cash moves from held to submitted to approved`() {
        val collection = collection(codAmount = 250_000, feeAmount = 22_000)
        assertEquals(272_000, collection.amount)
        assertEquals(CashCollectionStatus.HELD, collection.status)
        assertEquals(courier, collection.currentCustodianUserId)

        val remittance = CashRemittance.create("REM-1", courier, hub, listOf(collection), 272_000, now)
        collection.submitToHub(remittance.id, hub, now)
        assertEquals(CashCollectionStatus.SUBMITTED, collection.status)
        assertNull(collection.currentCustodianUserId)
        assertEquals(hub, collection.currentHubId)

        collection.approve(UUID.randomUUID(), now)
        remittance.approve(UUID.randomUUID(), now)
        assertEquals(CashCollectionStatus.APPROVED, collection.status)
        assertEquals(CashRemittanceStatus.APPROVED, remittance.status)
    }

    @Test
    fun `rejected cash stays at the hub and can be resubmitted`() {
        val collection = collection(codAmount = 100_000, feeAmount = 0)
        collection.submitToHub(UUID.randomUUID(), hub, now)

        collection.rejectToHub(hub, UUID.randomUUID(), "Cash mismatch", now)
        assertEquals(CashCollectionStatus.HELD, collection.status)
        assertEquals(hub, collection.currentHubId)
        assertNull(collection.currentRemittanceId)
        assertEquals("Cash mismatch", collection.lastRejectionReason)

        collection.resubmitFromHub(now)
        assertEquals(CashCollectionStatus.SUBMITTED, collection.status)
    }

    @Test
    fun `a remittance must declare exactly the cash it contains`() {
        val collection = collection(codAmount = 100_000, feeAmount = 0)
        assertThrows(IllegalArgumentException::class.java) {
            CashRemittance.create("REM-2", courier, hub, listOf(collection), 90_000, now)
        }
    }

    @Test
    fun `only approved cash can be settled, and only once`() {
        val collection = collection(codAmount = 100_000, feeAmount = 0)
        assertThrows(IllegalStateException::class.java) { collection.includeInStatement(UUID.randomUUID(), now) }

        collection.submitToHub(UUID.randomUUID(), hub, now)
        collection.approve(UUID.randomUUID(), now)
        collection.includeInStatement(UUID.randomUUID(), now)
        assertThrows(IllegalStateException::class.java) { collection.includeInStatement(UUID.randomUUID(), now) }
    }

    private fun collection(codAmount: Long, feeAmount: Long) = CashCollection.fromDelivery(
        shipmentId = UUID.randomUUID(),
        trackingCode = "PAVEX2609250123456789",
        merchantId = UUID.randomUUID(),
        deliveryAttemptId = UUID.randomUUID(),
        courierUserId = courier,
        hubId = hub,
        codAmount = codAmount,
        feeAmount = feeAmount,
        collectedAt = now,
    )
}
