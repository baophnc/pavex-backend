package com.pavex.shipment.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** A walk-in customer's shipment, booked by hub staff at the origin hub's counter. */
class CounterShipmentTest {
    @Test
    fun `parcels handed in at the counter start in the origin hub's custody`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0, parcelCount = 2, counter = CounterAcceptance(22_000))

        assertEquals(ShipmentChannel.COUNTER, shipment.channel)
        assertNull(shipment.merchantId)
        assertEquals(ShipmentStatus.AT_ORIGIN_HUB, shipment.status)
        shipment.parcels.forEach {
            assertEquals(ParcelStatus.AT_ORIGIN_HUB, it.status)
            assertEquals(ParcelStage.AT_HUB, it.stage)
            assertEquals(ParcelCustodyType.HUB, it.custodyType)
            assertEquals(shipment.originHubId, it.currentHubId)
            assertEquals(shipment.originHubId, it.custodyReferenceId)
        }
        assertEquals(listOf("shipment.counter-accepted"), shipment.events.map { it.code })
        assertEquals(shipment.originHubId, shipment.events.single().hubId)
    }

    @Test
    fun `the fee the sender pays at the counter is recorded and never charged to a merchant`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 150_000, counter = CounterAcceptance(22_000))

        assertEquals(22_000L, shipment.counterFeePaidAmount)
        assertEquals(shipment.createdAt, shipment.counterFeePaidAt)
        assertEquals(0L, shipment.merchantShippingFee())
        assertEquals(150_000L, shipment.recipientCollectAmount)
    }

    @Test
    fun `the counter must take exactly the fee when the sender pays, and nothing when the recipient does`() {
        assertThrows(IllegalArgumentException::class.java) {
            ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0, counter = CounterAcceptance(20_000))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 0, counter = CounterAcceptance(22_000))
        }

        val recipientPays = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 0, counter = CounterAcceptance(0))
        assertNull(recipientPays.counterFeePaidAmount)
        assertEquals(22_000L, recipientPays.recipientCollectAmount)
    }

    @Test
    fun `a returned recipient-paid counter shipment leaves the fee for the sender to pay at the counter`() {
        val recipientPays = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 0, counter = CounterAcceptance(0))
        assertEquals(0L, recipientPays.counterSenderFeeDue())

        recipientPays.status = ShipmentStatus.RETURNED
        assertEquals(22_000L, recipientPays.counterSenderFeeDue())
        assertEquals(0L, recipientPays.merchantShippingFee())

        val senderPaid = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0, counter = CounterAcceptance(22_000))
        senderPaid.status = ShipmentStatus.RETURNED
        assertEquals(0L, senderPaid.counterSenderFeeDue())

        val shop = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 0)
        shop.status = ShipmentStatus.RETURNED
        assertEquals(0L, shop.counterSenderFeeDue())
        assertEquals(22_000L, shop.merchantShippingFee())
    }

    @Test
    fun `a shop shipment still starts awaiting pickup with its merchant`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0)

        assertEquals(ShipmentChannel.MERCHANT_WEB, shipment.channel)
        assertEquals(ShipmentStatus.AWAITING_PICKUP, shipment.status)
        assertEquals(listOf("shipment.created"), shipment.events.map { it.code })
    }
}
