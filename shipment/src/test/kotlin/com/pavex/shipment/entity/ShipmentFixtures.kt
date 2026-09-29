package com.pavex.shipment.entity

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** A shipment with a 22,000 VND shipping fee (one parcel by default), for money-rule tests. */
object ShipmentFixtures {
    fun moneyShipment(payer: ShippingFeePayer, codAmount: Long, parcelCount: Int = 1): Shipment {
        val now = Instant.parse("2026-09-25T03:00:00Z")
        return Shipment.createFoundation(
            id = UUID.randomUUID(),
            trackingCode = "PAVEX2609250123456789",
            merchantId = UUID.randomUUID(),
            createdByUserId = UUID.randomUUID(),
            idempotencyKey = "shipment-money-001",
            requestFingerprint = "C".repeat(64),
            merchantReference = null,
            pickupAddressId = null,
            sender = ShipmentAddressSnapshot.create("A", "0901234567", "A", "48", "Đà Nẵng", "20410", "Hội An"),
            recipient = ShipmentAddressSnapshot.create("B", "0912345678", "B", "79", "Hồ Chí Minh", "26740", "Sài Gòn"),
            shippingFeePayer = payer,
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
                baseFee = 20_000,
                additionalWeightFee = 0,
                areaSurcharge = 0,
                codFee = 2_000,
                insuranceFee = 0,
                shippingFee = 22_000,
                codAmount = codAmount,
                declaredValue = 0,
                estimatedMinDays = 1,
                estimatedMaxDays = 2,
                quoteExpiresAt = now.plusSeconds(1800),
            ),
            origin = ShipmentNetworkPointSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ORI", "Origin", ShipmentCoverageTier.STANDARD),
            destination = ShipmentNetworkPointSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DST", "Destination", ShipmentCoverageTier.STANDARD),
            parcelDrafts = List(parcelCount) {
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
                )
            },
            note = null,
            createdAt = now,
        )
    }
}
