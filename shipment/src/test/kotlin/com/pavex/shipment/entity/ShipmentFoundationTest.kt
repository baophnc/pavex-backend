package com.pavex.shipment.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ShipmentFoundationTest {
    @Test
    fun `foundation snapshots human labels and creates parcel codes`() {
        val now = Instant.parse("2026-08-29T03:00:00Z")
        val shipment = Shipment.createFoundation(
            id = UUID.randomUUID(),
            trackingCode = "PAVEX2608290123456789",
            merchantId = UUID.randomUUID(),
            createdByUserId = UUID.randomUUID(),
            idempotencyKey = "shipment-create-001",
            requestFingerprint = "A".repeat(64),
            merchantReference = "ORDER-001",
            pickupAddressId = null,
            sender = ShipmentAddressSnapshot.create(
                "Nguyễn Văn A",
                "+84901234567",
                "12 Trần Phú",
                "48",
                "Đà Nẵng",
                "20410",
                "Hội An",
            ),
            recipient = ShipmentAddressSnapshot.create(
                "Trần Văn B",
                "0912345678",
                "01 Lê Lợi",
                "79",
                "Hồ Chí Minh",
                "26740",
                "Sài Gòn",
            ),
            shippingFeePayer = ShippingFeePayer.SENDER,
            price = ShipmentPriceSnapshot(
                quoteId = UUID.randomUUID(),
                quoteRequestId = UUID.randomUUID(),
                ratePlanId = UUID.randomUUID(),
                rateCode = "DEMO-2026-08",
                rateRevision = 1,
                rateVersion = "DEMO-2026-08-r1",
                currency = "VND",
                serviceLevel = ShipmentServiceLevel.EXPRESS,
                pricingZone = ShipmentPricingZone.CROSS_REGION,
                baseFee = 50_000,
                additionalWeightFee = 9_000,
                areaSurcharge = 8_000,
                codFee = 5_000,
                insuranceFee = 5_000,
                shippingFee = 77_000,
                codAmount = 2_000_000,
                declaredValue = 2_000_000,
                estimatedMinDays = 1,
                estimatedMaxDays = 2,
                quoteExpiresAt = now.plusSeconds(1800),
            ),
            origin = ShipmentNetworkPointSnapshot(
                serviceAreaId = UUID.randomUUID(),
                regionId = UUID.randomUUID(),
                hubId = UUID.randomUUID(),
                hubCode = "DN-HOIAN",
                hubName = "Hội An",
                coverageTier = ShipmentCoverageTier.STANDARD,
            ),
            destination = ShipmentNetworkPointSnapshot(
                serviceAreaId = UUID.randomUUID(),
                regionId = UUID.randomUUID(),
                hubId = UUID.randomUUID(),
                hubCode = "HCM-SAIGON",
                hubName = "Sài Gòn",
                coverageTier = ShipmentCoverageTier.STANDARD,
            ),
            parcelDrafts = listOf(
                parcel("Quần áo", BigDecimal("1.000")),
                parcel("Mỹ phẩm", BigDecimal("2.000")),
            ),
            note = "Gọi trước khi giao",
            createdAt = now,
        )

        assertEquals("Đà Nẵng", shipment.sender.provinceName)
        assertEquals("Hội An", shipment.originHubName)
        assertEquals("Sài Gòn", shipment.destinationHubName)
        assertEquals("0901234567", shipment.sender.contactPhone)
        assertEquals(listOf("PAVEX2608290123456789-01", "PAVEX2608290123456789-02"), shipment.parcels.map { it.code })
        assertEquals("shipment.created", shipment.events.single().code)
        assertTrue(shipment.events.single().locationName == "Hội An")

        val reservationId = UUID.randomUUID()
        shipment.assignReservedRoute(
            routeRevision = 1,
            capacityReservationBatchId = reservationId,
            reservedWeightKg = BigDecimal("3.000"),
            readyAt = now,
            estimatedDeliveryAt = now.plusSeconds(7_200),
            estimatedTransitMinutes = 120,
            distanceKm = BigDecimal("45.500"),
            updatedAt = now,
        )

        assertEquals(ShipmentRoutingStatus.RESERVED, shipment.routingStatus)
        assertEquals(reservationId, shipment.capacityReservationBatchId)
        assertEquals(BigDecimal("3.000"), shipment.reservedWeightKg)
    }

    @Test
    fun `shipment can only be cancelled before pickup`() {
        val now = Instant.parse("2026-08-29T03:00:00Z")
        val actor = UUID.randomUUID()
        val shipment = Shipment.createFoundation(
            id = UUID.randomUUID(),
            trackingCode = "PAVEX2608290123456789",
            merchantId = UUID.randomUUID(),
            createdByUserId = actor,
            idempotencyKey = "shipment-cancel-001",
            requestFingerprint = "B".repeat(64),
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
            origin = ShipmentNetworkPointSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ORI", "Origin", ShipmentCoverageTier.STANDARD),
            destination = ShipmentNetworkPointSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DST", "Destination", ShipmentCoverageTier.STANDARD),
            parcelDrafts = listOf(parcel("Box", BigDecimal("1.000"))),
            note = null,
            createdAt = now,
        )

        shipment.cancel("Customer changed plan", actor, now.plusSeconds(60))

        assertEquals(ShipmentStatus.CANCELLED, shipment.status)
        assertEquals(ShipmentLifecycleStatus.CANCELLED, shipment.lifecycleStatus)
        assertEquals(ParcelStatus.CANCELLED, shipment.parcels.single().status)
        assertEquals(ParcelStage.CANCELLED, shipment.parcels.single().stage)
        assertEquals("shipment.cancelled", shipment.events.last().code)
        assertEquals("Customer changed plan", shipment.cancellationReason)
    }

    @Test
    fun `recipient-paid shipment collects COD plus the shipping fee and charges the merchant nothing`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 250_000)

        assertEquals(272_000, shipment.recipientCollectAmount)
        assertEquals(22_000, shipment.recipientShippingFee())
        assertEquals(0, shipment.merchantShippingFee())
        assertEquals(ShipmentCollectionStatus.PENDING, shipment.collectionStatus())
    }

    @Test
    fun `sender-paid shipment collects only COD and charges the merchant the fee`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 250_000)

        assertEquals(250_000, shipment.recipientCollectAmount)
        assertEquals(0, shipment.recipientShippingFee())
        assertEquals(22_000, shipment.merchantShippingFee())
    }

    @Test
    fun `nothing to collect and shipments cancelled before pickup are not charged`() {
        val noCod = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0)
        assertEquals(ShipmentCollectionStatus.NOT_REQUIRED, noCod.collectionStatus())

        val cancelled = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 100_000)
        cancelled.cancel("Merchant cancelled", UUID.randomUUID(), cancelled.createdAt.plusSeconds(60))

        assertEquals(ShipmentCollectionStatus.VOIDED, cancelled.collectionStatus())
        assertEquals(0, cancelled.merchantShippingFee())
    }

    @Test
    fun `a shipment cancelled after pickup charges the merchant the shipping fee`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 100_000)
        val parcel = shipment.parcels.single()
        var at = shipment.createdAt
        listOf(ParcelStatus.PICKED_UP, ParcelStatus.AT_ORIGIN_HUB).forEach {
            at = at.plusSeconds(600)
            shipment.move(parcel.id, it, at)
        }

        shipment.cancelRecovered("Merchant cancelled after pickup", UUID.randomUUID(), at.plusSeconds(600))

        assertEquals(ShipmentStatus.CANCELLED, shipment.status)
        assertEquals(true, shipment.cancelledAfterPickup)
        assertEquals(ShipmentCollectionStatus.VOIDED, shipment.collectionStatus())
        assertEquals(22_000, shipment.merchantShippingFee())
    }

    @Test
    fun `a shipment with delivered and returned parcels closes and is settled`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0, parcelCount = 2)
        val (delivered, returned) = shipment.parcels
        var at = shipment.createdAt
        val toDestination = listOf(
            ParcelStatus.PICKED_UP,
            ParcelStatus.AT_ORIGIN_HUB,
            ParcelStatus.IN_TRANSIT,
            ParcelStatus.AT_DESTINATION_HUB,
        )
        (toDestination + listOf(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED)).forEach {
            at = at.plusSeconds(600)
            shipment.move(delivered.id, it, at)
        }
        (toDestination + ParcelStatus.RETURNING).forEach {
            at = at.plusSeconds(600)
            shipment.move(returned.id, it, at)
        }
        assertEquals(ShipmentLifecycleStatus.ACTIVE, shipment.lifecycleStatus)

        shipment.move(returned.id, ParcelStatus.RETURNED, at.plusSeconds(600))

        assertEquals(ShipmentStatus.PARTIALLY_DELIVERED, shipment.status)
        assertEquals(ShipmentLifecycleStatus.COMPLETED, shipment.lifecycleStatus)
        assertEquals("shipment.completed", shipment.events.last().code)
        assertEquals(22_000, shipment.merchantShippingFee())
    }

    private fun Shipment.move(parcelId: UUID, status: ParcelStatus, at: Instant) =
        transitionParcel(
            parcelId = parcelId,
            nextStatus = status,
            eventCode = "parcel.test-transition",
            description = "Test transition to $status",
            actorUserId = UUID.randomUUID(),
            hubId = destinationHubId,
            locationName = "Hub",
            occurredAt = at,
            recordedAt = at,
        )

    @Test
    fun `the full amount is collected exactly once`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 250_000)
        val parcel = shipment.parcels.single()
        val courier = UUID.randomUUID()
        val attemptId = UUID.randomUUID()
        val at = shipment.createdAt.plusSeconds(3_600)

        assertThrows(IllegalArgumentException::class.java) {
            shipment.recordCollection(250_000, courier, attemptId, parcel.id, shipment.destinationHubId, "Sài Gòn", at, at)
        }
        shipment.recordCollection(272_000, courier, attemptId, parcel.id, shipment.destinationHubId, "Sài Gòn", at, at)

        assertEquals(ShipmentCollectionStatus.COLLECTED, shipment.collectionStatus())
        assertEquals(272_000L, shipment.collectedAmount)
        assertEquals(courier, shipment.collectedByUserId)
        assertEquals(attemptId, shipment.collectionAttemptId)
        assertEquals("shipment.cash-collected", shipment.events.last().code)
        assertThrows(IllegalStateException::class.java) {
            shipment.recordCollection(272_000, courier, UUID.randomUUID(), parcel.id, shipment.destinationHubId, "Sài Gòn", at, at)
        }
    }

    @Test
    fun `a shipment with nothing to collect cannot record a collection`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0)
        val at = shipment.createdAt.plusSeconds(60)

        assertThrows(IllegalStateException::class.java) {
            shipment.recordCollection(0, UUID.randomUUID(), UUID.randomUUID(), shipment.parcels.single().id, shipment.destinationHubId, "", at, at)
        }
    }

    private fun parcel(description: String, weight: BigDecimal) = ParcelDraft(
        description = description,
        quantity = 1,
        category = GoodsCategory.OTHER,
        isFragile = false,
        actualWeightKg = weight,
        volumetricWeightKg = BigDecimal("0.500"),
        chargeableWeightKg = weight.max(BigDecimal("0.500")),
        lengthCm = BigDecimal("20.00"),
        widthCm = BigDecimal("20.00"),
        heightCm = BigDecimal("20.00"),
    )
}
