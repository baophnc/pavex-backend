package com.pavex.shipment.service

import com.pavex.contracts.network.ShipmentOperationalHubSnapshot
import com.pavex.shipment.entity.GoodsCategory
import com.pavex.shipment.entity.ParcelDraft
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentAddressSnapshot
import com.pavex.shipment.entity.ShipmentCoverageTier
import com.pavex.shipment.entity.ShipmentNetworkPointSnapshot
import com.pavex.shipment.entity.ShipmentPriceSnapshot
import com.pavex.shipment.entity.ShipmentPricingZone
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShipmentStatus
import com.pavex.shipment.entity.ShippingFeePayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ParcelScanLifecycleTest {
    @Test
    fun `parcel transitions recalculate shipment status and append timeline events`() {
        val now = Instant.parse("2026-08-29T04:00:00Z")
        val actor = UUID.randomUUID()
        val originHubId = UUID.randomUUID()
        val destinationHubId = UUID.randomUUID()
        val transitHubId = UUID.randomUUID()
        val shipment = shipment(now, actor, originHubId, destinationHubId)
        val parcel = shipment.parcels.single()

        transition(shipment, parcel.id, ParcelStatus.PICKED_UP, actor, originHubId, "Origin", now.plusSeconds(10))
        assertEquals(ParcelStatus.PICKED_UP, parcel.status)
        assertEquals(ShipmentStatus.PICKED_UP, shipment.status)

        transition(shipment, parcel.id, ParcelStatus.AT_ORIGIN_HUB, actor, originHubId, "Origin", now.plusSeconds(20))
        transition(shipment, parcel.id, ParcelStatus.IN_TRANSIT, actor, originHubId, "Origin", now.plusSeconds(30))
        transition(shipment, parcel.id, ParcelStatus.AT_TRANSIT_HUB, actor, transitHubId, "Gateway", now.plusSeconds(40))
        assertEquals(ParcelStatus.AT_TRANSIT_HUB, parcel.status)
        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.status)
        transition(shipment, parcel.id, ParcelStatus.IN_TRANSIT, actor, transitHubId, "Gateway", now.plusSeconds(50))
        transition(shipment, parcel.id, ParcelStatus.AT_DESTINATION_HUB, actor, destinationHubId, "Destination", now.plusSeconds(60))

        assertEquals(ParcelStatus.AT_DESTINATION_HUB, parcel.status)
        assertEquals(ShipmentStatus.AT_DESTINATION_HUB, shipment.status)
        assertEquals("parcel.at-destination-hub", shipment.events.last().code)
        assertEquals("Destination", shipment.events.last().locationName)
    }

    @Test
    fun `scan hub policy accepts route hubs only for transit movement`() {
        val origin = UUID.randomUUID()
        val destination = UUID.randomUUID()
        val middle = UUID.randomUUID()
        val routeHub = hub(middle, active = true, onReservedRoute = true)
        val unrelatedHub = hub(UUID.randomUUID(), active = true, onReservedRoute = false)

        assertTrue(ParcelScanPolicy.isHubAllowed(origin, destination, routeHub, ParcelStatus.IN_TRANSIT))
        assertTrue(ParcelScanPolicy.isHubAllowed(origin, destination, routeHub, ParcelStatus.AT_TRANSIT_HUB))
        assertFalse(ParcelScanPolicy.isHubAllowed(origin, destination, unrelatedHub, ParcelStatus.IN_TRANSIT))
        assertFalse(ParcelScanPolicy.isHubAllowed(origin, destination, unrelatedHub, ParcelStatus.AT_TRANSIT_HUB))
        assertFalse(ParcelScanPolicy.isHubAllowed(origin, destination, routeHub, ParcelStatus.AT_DESTINATION_HUB))
        assertTrue(ParcelScanPolicy.isHubAllowed(origin, destination, hub(destination), ParcelStatus.AT_DESTINATION_HUB))
        assertFalse(ParcelScanPolicy.isHubAllowed(origin, destination, hub(origin, active = false), null))
    }

    private fun transition(
        shipment: Shipment,
        parcelId: UUID,
        status: ParcelStatus,
        actor: UUID,
        hubId: UUID,
        hubName: String,
        at: Instant,
    ) {
        shipment.transitionParcel(
            parcelId = parcelId,
            nextStatus = status,
            eventCode = "parcel.${status.name.lowercase().replace('_', '-')}",
            description = "scan",
            actorUserId = actor,
            hubId = hubId,
            locationName = hubName,
            occurredAt = at,
            recordedAt = at,
        )
    }

    private fun hub(id: UUID, active: Boolean = true, onReservedRoute: Boolean = false) =
        ShipmentOperationalHubSnapshot(id, "HUB", "Hub", active, onReservedRoute)

    private fun shipment(now: Instant, actor: UUID, originHubId: UUID, destinationHubId: UUID): Shipment =
        Shipment.createFoundation(
            id = UUID.randomUUID(),
            trackingCode = "PAVEX2608290123456789",
            merchantId = UUID.randomUUID(),
            createdByUserId = actor,
            idempotencyKey = "shipment-scan-001",
            requestFingerprint = "C".repeat(64),
            merchantReference = null,
            pickupAddressId = null,
            sender = ShipmentAddressSnapshot.create("A", "0901234567", "A", "48", "Đà Nẵng", "20410", "Hội An"),
            recipient = ShipmentAddressSnapshot.create("B", "0912345678", "B", "79", "Hồ Chí Minh", "26740", "Sài Gòn"),
            shippingFeePayer = ShippingFeePayer.SENDER,
            price = ShipmentPriceSnapshot(
                quoteId = UUID.randomUUID(),
                quoteRequestId = UUID.randomUUID(),
                ratePlanId = UUID.randomUUID(),
                rateCode = "RATE",
                rateRevision = 1,
                rateVersion = "RATE-r1",
                currency = "VND",
                serviceLevel = ShipmentServiceLevel.STANDARD,
                pricingZone = ShipmentPricingZone.CROSS_REGION,
                baseFee = 10_000,
                additionalWeightFee = 0,
                areaSurcharge = 0,
                codFee = 0,
                insuranceFee = 0,
                shippingFee = 10_000,
                codAmount = 0,
                declaredValue = 0,
                estimatedMinDays = 1,
                estimatedMaxDays = 2,
                quoteExpiresAt = now.plusSeconds(1800),
            ),
            origin = ShipmentNetworkPointSnapshot(UUID.randomUUID(), UUID.randomUUID(), originHubId, "ORI", "Origin", ShipmentCoverageTier.STANDARD),
            destination = ShipmentNetworkPointSnapshot(UUID.randomUUID(), UUID.randomUUID(), destinationHubId, "DST", "Destination", ShipmentCoverageTier.STANDARD),
            parcelDrafts = listOf(
                ParcelDraft(
                    description = "Box",
                    quantity = 1,
                    category = GoodsCategory.OTHER,
                    isFragile = false,
                    actualWeightKg = BigDecimal("1.000"),
                    volumetricWeightKg = BigDecimal("0.500"),
                    chargeableWeightKg = BigDecimal("1.000"),
                    lengthCm = BigDecimal("20.00"),
                    widthCm = BigDecimal("20.00"),
                    heightCm = BigDecimal("20.00"),
                ),
            ),
            note = null,
            createdAt = now,
        )
}
