package com.pavex.shipment.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** An order sent and delivered by the same post office never takes a truck. */
class SameHubShipmentTest {
    @Test
    fun `a shop parcel received at the hub that also delivers it goes out for delivery from there`() {
        val shipment = sameHub(ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0))
        val parcel = shipment.parcels.single()
        var at = shipment.createdAt
        listOf(ParcelStatus.PICKED_UP, ParcelStatus.AT_ORIGIN_HUB).forEach {
            at = at.plusSeconds(600)
            shipment.move(parcel.id, it, at)
        }

        assertTrue(parcel.isReadyForDelivery())
        listOf(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED).forEach {
            at = at.plusSeconds(600)
            shipment.move(parcel.id, it, at)
        }

        assertEquals(ShipmentStatus.DELIVERED, shipment.status)
        assertEquals(ShipmentLifecycleStatus.COMPLETED, shipment.lifecycleStatus)
    }

    @Test
    fun `a counter parcel goes out for delivery from the hub it was handed in at`() {
        val shipment = sameHub(
            ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0, counter = CounterAcceptance(22_000)),
        )
        val parcel = shipment.parcels.single()

        assertTrue(parcel.canTransitionTo(ParcelStatus.OUT_FOR_DELIVERY))
        shipment.move(parcel.id, ParcelStatus.OUT_FOR_DELIVERY, shipment.createdAt.plusSeconds(600))

        assertEquals(ShipmentStatus.OUT_FOR_DELIVERY, shipment.status)
    }

    @Test
    fun `a parcel at an origin hub that does not deliver it still has to take the truck`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0)
        val parcel = shipment.parcels.single()
        shipment.move(parcel.id, ParcelStatus.PICKED_UP, shipment.createdAt.plusSeconds(600))
        shipment.move(parcel.id, ParcelStatus.AT_ORIGIN_HUB, shipment.createdAt.plusSeconds(1200), shipment.originHubId)

        assertFalse(parcel.isReadyForDelivery())
        assertFalse(parcel.canTransitionTo(ParcelStatus.OUT_FOR_DELIVERY))
        assertTrue(parcel.canTransitionTo(ParcelStatus.IN_TRANSIT))
        assertThrows(IllegalStateException::class.java) {
            shipment.move(parcel.id, ParcelStatus.OUT_FOR_DELIVERY, shipment.createdAt.plusSeconds(1800))
        }
    }

    private fun sameHub(shipment: Shipment): Shipment = shipment.apply {
        destinationHubId = originHubId
        destinationHubName = originHubName
    }

    private fun Shipment.move(parcelId: UUID, status: ParcelStatus, at: Instant, hubId: UUID = destinationHubId) =
        transitionParcel(
            parcelId = parcelId,
            nextStatus = status,
            eventCode = "parcel.test-transition",
            description = "Test transition to $status",
            actorUserId = UUID.randomUUID(),
            hubId = hubId,
            locationName = "Hub",
            occurredAt = at,
            recordedAt = at,
        )
}
