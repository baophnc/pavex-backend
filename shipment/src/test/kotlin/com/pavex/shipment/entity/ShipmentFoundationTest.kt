package com.pavex.shipment.entity

import org.junit.jupiter.api.Assertions.assertEquals
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
