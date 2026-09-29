package com.pavex.shipment.service

import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.ShipmentFixtures
import com.pavex.shipment.entity.ShippingFeePayer
import com.pavex.shipment.exception.InvalidShipmentRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class DeliveryCollectionPolicyTest {
    private val delivered = DeliveryAttemptOutcome.DELIVERED
    private val failed = DeliveryAttemptOutcome.FAILED

    @Test
    fun `first successful delivery must report exactly the amount to collect`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 250_000)

        assertEquals(272_000L, DeliveryCollectionPolicy.resolve(shipment, delivered, 272_000))
        assertEquals("collection_amount_mismatch", codeOf { DeliveryCollectionPolicy.resolve(shipment, delivered, 250_000) })
        assertEquals("collection_amount_mismatch", codeOf { DeliveryCollectionPolicy.resolve(shipment, delivered, null) })
    }

    @Test
    fun `later parcels and failed attempts collect nothing`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 250_000)
        val at = shipment.createdAt.plusSeconds(60)
        shipment.recordCollection(272_000, UUID.randomUUID(), UUID.randomUUID(), shipment.parcels.single().id, shipment.destinationHubId, "", at, at)

        assertNull(DeliveryCollectionPolicy.resolve(shipment, delivered, null))
        assertNull(DeliveryCollectionPolicy.resolve(shipment, delivered, 0))
        assertEquals("collection_not_expected", codeOf { DeliveryCollectionPolicy.resolve(shipment, delivered, 272_000) })

        val pending = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 100_000)
        assertNull(DeliveryCollectionPolicy.resolve(pending, failed, null))
        assertEquals("collection_not_expected", codeOf { DeliveryCollectionPolicy.resolve(pending, failed, 100_000) })
    }

    @Test
    fun `a shipment with nothing to collect accepts no amount`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0)

        assertNull(DeliveryCollectionPolicy.resolve(shipment, delivered, null))
        assertEquals("collection_not_expected", codeOf { DeliveryCollectionPolicy.resolve(shipment, delivered, 22_000) })
    }

    private fun codeOf(block: () -> Unit): String =
        assertThrows(InvalidShipmentRequestException::class.java) { block() }.code
}
