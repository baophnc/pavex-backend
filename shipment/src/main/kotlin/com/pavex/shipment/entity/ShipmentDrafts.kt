package com.pavex.shipment.entity

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ShipmentPriceSnapshot(
    val quoteId: UUID,
    val quoteRequestId: UUID,
    val ratePlanId: UUID,
    val rateCode: String,
    val rateRevision: Int,
    val rateVersion: String,
    val currency: String,
    val serviceLevel: ShipmentServiceLevel,
    val pricingZone: ShipmentPricingZone,
    val baseFee: Long,
    val additionalWeightFee: Long,
    val areaSurcharge: Long,
    val codFee: Long,
    val insuranceFee: Long,
    val shippingFee: Long,
    val codAmount: Long,
    val declaredValue: Long,
    val estimatedMinDays: Int,
    val estimatedMaxDays: Int,
    val quoteExpiresAt: Instant,
)

data class ShipmentNetworkPointSnapshot(
    val serviceAreaId: UUID,
    val regionId: UUID,
    val hubId: UUID,
    val hubCode: String,
    val hubName: String,
    val coverageTier: ShipmentCoverageTier,
)

data class ParcelDraft(
    val description: String,
    val quantity: Int,
    val category: GoodsCategory,
    val isFragile: Boolean,
    val actualWeightKg: BigDecimal,
    val volumetricWeightKg: BigDecimal,
    val chargeableWeightKg: BigDecimal,
    val lengthCm: BigDecimal,
    val widthCm: BigDecimal,
    val heightCm: BigDecimal,
)
