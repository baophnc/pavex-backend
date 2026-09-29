package com.pavex.billing.messaging

import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.MerchantFeeCharge
import com.pavex.billing.repository.CashCollectionRepository
import com.pavex.billing.repository.MerchantFeeChargeRepository
import com.pavex.billing.repository.ProcessedEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ShipmentEventIngestionTest {
    private val processed = mutableSetOf<UUID>()
    private val collections = mutableListOf<CashCollection>()
    private val charges = mutableListOf<MerchantFeeCharge>()
    private val ingestion = ShipmentEventIngestion(
        processedEvents = mock(ProcessedEventRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "existsById" -> invocation.getArgument<UUID>(0) in processed
                "save" -> invocation.getArgument<com.pavex.billing.entity.ProcessedEvent>(0).also { processed += it.eventId }
                else -> null
            }
        },
        collections = mock(CashCollectionRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "existsByShipmentId" -> collections.any { it.shipmentId == invocation.getArgument(0) }
                "save" -> invocation.getArgument<CashCollection>(0).also { collections += it }
                else -> null
            }
        },
        feeCharges = mock(MerchantFeeChargeRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "existsByShipmentId" -> charges.any { it.shipmentId == invocation.getArgument(0) }
                "save" -> invocation.getArgument<MerchantFeeCharge>(0).also { charges += it }
                else -> null
            }
        },
        clock = Clock.fixed(Instant.parse("2026-09-25T05:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `a delivered collection is held by the courier, once per event`() {
        val eventId = UUID.randomUUID()
        val event = cashCollected()

        assertTrue(ingestion.cashCollected(eventId, event))
        assertFalse(ingestion.cashCollected(eventId, event))

        val collection = collections.single()
        assertEquals(272_000, collection.amount)
        assertEquals(250_000, collection.codAmount)
        assertEquals(22_000, collection.feeAmount)
        assertEquals(event.courierUserId, collection.currentCustodianUserId)
    }

    @Test
    fun `a redelivered collection under a new event id is not recorded twice`() {
        val event = cashCollected()
        ingestion.cashCollected(UUID.randomUUID(), event)

        assertFalse(ingestion.cashCollected(UUID.randomUUID(), event))
        assertEquals(1, collections.size)
    }

    @Test
    fun `only shipments that charge the merchant create a fee`() {
        assertTrue(ingestion.shipmentClosed(UUID.randomUUID(), closed(merchantShippingFee = 22_000)))
        assertFalse(ingestion.shipmentClosed(UUID.randomUUID(), closed(merchantShippingFee = 0)))

        assertEquals(22_000, charges.single().amount)
    }

    private fun cashCollected() = CashCollectedEvent(
        shipmentId = UUID.randomUUID(),
        trackingCode = "PAVEX2609250123456789",
        merchantId = UUID.randomUUID(),
        courierUserId = UUID.randomUUID(),
        hubId = UUID.randomUUID(),
        attemptId = UUID.randomUUID(),
        amount = 272_000,
        codAmount = 250_000,
        recipientShippingFee = 22_000,
        collectedAt = Instant.parse("2026-09-25T04:00:00Z"),
    )

    private fun closed(merchantShippingFee: Long) = ShipmentClosedEvent(
        shipmentId = UUID.randomUUID(),
        trackingCode = "PAVEX2609250123456789",
        merchantId = UUID.randomUUID(),
        lifecycleStatus = "COMPLETED",
        finalStatus = "DELIVERED",
        shippingFeePayer = "SENDER",
        shippingFee = 22_000,
        merchantShippingFee = merchantShippingFee,
        codAmount = 0,
        amountToCollect = 0,
        collectedAmount = null,
        closedAt = Instant.parse("2026-09-25T04:00:00Z"),
    )
}
