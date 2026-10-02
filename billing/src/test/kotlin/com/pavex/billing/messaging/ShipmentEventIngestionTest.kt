package com.pavex.billing.messaging

import com.pavex.billing.client.OperationsClient
import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.CashCollectionStatus
import com.pavex.billing.entity.CashSource
import com.pavex.billing.entity.CodPayee
import com.pavex.billing.entity.CounterSettlement
import com.pavex.billing.entity.CounterSettlementKind
import com.pavex.billing.entity.CounterSettlementStatus
import com.pavex.billing.entity.CounterShipment
import com.pavex.billing.entity.MerchantFeeCharge
import com.pavex.billing.exception.InvalidBillingRequestException
import com.pavex.billing.repository.CashCollectionRepository
import com.pavex.billing.repository.CounterSettlementRepository
import com.pavex.billing.repository.CounterShipmentRepository
import com.pavex.billing.repository.MerchantFeeChargeRepository
import com.pavex.billing.repository.ProcessedEventRepository
import com.pavex.billing.service.CounterCashService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class ShipmentEventIngestionTest {
    private val now = Instant.parse("2026-09-25T05:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val processed = mutableSetOf<UUID>()
    private val collections = mutableListOf<CashCollection>()
    private val charges = mutableListOf<MerchantFeeCharge>()
    private val counters = mutableMapOf<UUID, CounterShipment>()
    private val settlements = mutableListOf<CounterSettlement>()
    private val hub = UUID.randomUUID()
    private val clerk = UUID.randomUUID()

    private val counterCash = CounterCashService(
        settlements = mock(CounterSettlementRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "existsByShipmentIdAndKind" -> settlements.any {
                    it.shipmentId == invocation.getArgument(0) && it.kind == invocation.getArgument(1)
                }
                "save", "saveAndFlush" -> invocation.getArgument<CounterSettlement>(0).also { s ->
                    if (settlements.none { it.id == s.id }) settlements += s
                }
                "findById" -> Optional.ofNullable(settlements.firstOrNull { it.id == invocation.getArgument(0) })
                else -> null
            }
        },
        counterShipments = counterRepository(),
        collections = collectionRepository(),
        // Every clerk in these tests serves the hub counter.
        operations = mock(OperationsClient::class.java) { invocation ->
            if (invocation.method.name == "canOperateAtHub") true else null
        },
        clock = clock,
    )

    private val ingestion = ShipmentEventIngestion(
        processedEvents = mock(ProcessedEventRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "existsById" -> invocation.getArgument<UUID>(0) in processed
                "save" -> invocation.getArgument<com.pavex.billing.entity.ProcessedEvent>(0).also { processed += it.eventId }
                else -> null
            }
        },
        collections = collectionRepository(),
        feeCharges = mock(MerchantFeeChargeRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "existsByShipmentId" -> charges.any { it.shipmentId == invocation.getArgument(0) }
                "save" -> invocation.getArgument<MerchantFeeCharge>(0).also { charges += it }
                else -> null
            }
        },
        counterShipments = counterRepository(),
        counterCash = counterCash,
        clock = clock,
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
        assertEquals(CodPayee.MERCHANT, collection.codPayee)
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

    @Test
    fun `a fee paid at the counter is cash the hub holds, with no courier`() {
        val event = counterAccepted(feePaid = 35_000)

        assertTrue(ingestion.counterAccepted(UUID.randomUUID(), event))

        val cash = collections.single()
        assertEquals(CashSource.COUNTER, cash.source)
        assertEquals(35_000, cash.amount)
        assertNull(cash.merchantId)
        assertNull(cash.currentCustodianUserId)
        assertEquals(hub, cash.currentHubId)
        assertEquals("CNT-${event.trackingCode}", cash.reference)
        assertEquals("0912345678", counters.getValue(event.shipmentId).senderPhone)
    }

    @Test
    fun `a walk-in sender's COD is paid back at the counter once finance has the cash`() {
        val booked = counterAccepted(feePaid = 35_000, cod = 500_000)
        ingestion.counterAccepted(UUID.randomUUID(), booked)
        ingestion.cashCollected(UUID.randomUUID(), cashCollected(shipmentId = booked.shipmentId, merchantId = null, cod = 500_000, fee = 0))

        val delivery = collections.single { it.source == CashSource.DELIVERY }
        assertEquals(CodPayee.SENDER, delivery.codPayee)
        assertTrue(settlements.isEmpty())

        delivery.status = CashCollectionStatus.APPROVED
        counterCash.onApproved(listOf(delivery), now)
        counterCash.onApproved(listOf(delivery), now)

        val payout = settlements.single()
        assertEquals(CounterSettlementKind.PAYOUT_COD, payout.kind)
        assertEquals(500_000, payout.amount)
        assertEquals(hub, payout.hubId)

        val wrongPhone = assertThrows(InvalidBillingRequestException::class.java) {
            counterCash.complete(clerk, false, payout.id, "0999999999", null)
        }
        assertEquals("counter_phone_mismatch", wrongPhone.code)
        counterCash.complete(clerk, false, payout.id, "+84 912 345 678", "Đã trả đủ")
        assertEquals(CounterSettlementStatus.DONE, payout.status)
    }

    @Test
    fun `a returned shipment the recipient was to pay leaves a fee for the sender, not the merchant`() {
        val booked = counterAccepted(feePaid = 0)
        ingestion.counterAccepted(UUID.randomUUID(), booked)

        assertTrue(ingestion.shipmentClosed(UUID.randomUUID(), closed(shipmentId = booked.shipmentId, merchantId = null, counterSenderFeeDue = 35_000)))

        assertTrue(charges.isEmpty())
        val fee = settlements.single()
        assertEquals(CounterSettlementKind.COLLECT_RETURN_FEE, fee.kind)

        counterCash.complete(clerk, false, fee.id, "0912345678", null)
        val cash = collections.single()
        assertEquals(CashSource.COUNTER, cash.source)
        assertEquals("CNT-RET-${booked.trackingCode}", cash.reference)
        assertEquals(hub, cash.currentHubId)
    }

    private fun collectionRepository() = mock(CashCollectionRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "existsByShipmentIdAndSource" -> collections.any {
                it.shipmentId == invocation.getArgument(0) && it.source == invocation.getArgument(1)
            }
            "save" -> invocation.getArgument<CashCollection>(0).also { collections += it }
            else -> null
        }
    }

    private fun counterRepository() = mock(CounterShipmentRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "existsById" -> invocation.getArgument<UUID>(0) in counters
            "findById" -> Optional.ofNullable(counters[invocation.getArgument(0)])
            "save" -> invocation.getArgument<CounterShipment>(0).also { counters[it.shipmentId] = it }
            else -> null
        }
    }

    private fun counterAccepted(feePaid: Long, cod: Long = 0) = CounterAcceptedEvent(
        shipmentId = UUID.randomUUID(),
        trackingCode = "PAVEX2610020123456789",
        hubId = hub,
        clerkUserId = clerk,
        senderName = "Nguyễn Văn Gửi",
        senderPhone = "0912345678",
        shippingFeePayer = if (feePaid > 0) "SENDER" else "RECIPIENT",
        shippingFee = 35_000,
        feePaidAtCounter = feePaid,
        codAmount = cod,
        acceptedAt = Instant.parse("2026-09-25T03:00:00Z"),
    )

    private fun cashCollected(
        shipmentId: UUID = UUID.randomUUID(),
        merchantId: UUID? = UUID.randomUUID(),
        cod: Long = 250_000,
        fee: Long = 22_000,
    ) = CashCollectedEvent(
        shipmentId = shipmentId,
        trackingCode = "PAVEX2609250123456789",
        merchantId = merchantId,
        channel = if (merchantId == null) COUNTER else MERCHANT_WEB,
        courierUserId = UUID.randomUUID(),
        hubId = UUID.randomUUID(),
        attemptId = UUID.randomUUID(),
        amount = cod + fee,
        codAmount = cod,
        recipientShippingFee = fee,
        collectedAt = Instant.parse("2026-09-25T04:00:00Z"),
    )

    private fun closed(
        merchantShippingFee: Long = 0,
        shipmentId: UUID = UUID.randomUUID(),
        merchantId: UUID? = UUID.randomUUID(),
        counterSenderFeeDue: Long = 0,
    ) = ShipmentClosedEvent(
        shipmentId = shipmentId,
        trackingCode = "PAVEX2609250123456789",
        merchantId = merchantId,
        channel = if (merchantId == null) COUNTER else MERCHANT_WEB,
        lifecycleStatus = "COMPLETED",
        finalStatus = if (counterSenderFeeDue > 0) "RETURNED" else "DELIVERED",
        shippingFeePayer = "SENDER",
        shippingFee = 22_000,
        merchantShippingFee = merchantShippingFee,
        counterSenderFeeDue = counterSenderFeeDue,
        codAmount = 0,
        amountToCollect = 0,
        collectedAmount = null,
        closedAt = Instant.parse("2026-09-25T04:00:00Z"),
    )
}
