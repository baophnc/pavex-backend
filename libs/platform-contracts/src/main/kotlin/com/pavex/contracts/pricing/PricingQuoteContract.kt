package com.pavex.contracts.pricing

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class PricingQuoteServiceLevel { STANDARD, EXPRESS }
enum class PricingQuoteZone { SAME_PROVINCE, SAME_REGION, CROSS_REGION }
enum class PricingQuoteCoverageTier { STANDARD, REMOTE, ISLAND }

data class PricingQuoteLocationSnapshot(
    val provinceCode: String,
    val wardCode: String,
    val serviceAreaId: UUID,
    val regionId: UUID,
    val primaryHubId: UUID,
    val coverageTier: PricingQuoteCoverageTier,
)

data class PricingQuoteParcelSnapshot(
    val sequence: Int,
    val actualWeightKg: BigDecimal,
    val volumetricWeightKg: BigDecimal,
    val chargeableWeightKg: BigDecimal,
    val lengthCm: BigDecimal,
    val widthCm: BigDecimal,
    val heightCm: BigDecimal,
)

data class PricingQuoteFeeSnapshot(
    val baseFee: Long,
    val additionalWeightFee: Long,
    val areaSurcharge: Long,
    val codFee: Long,
    val insuranceFee: Long,
    val total: Long,
)

data class PricingQuoteSnapshot(
    val quoteId: UUID,
    val requestId: UUID,
    val ratePlanId: UUID,
    val rateCode: String,
    val rateRevision: Int,
    val rateVersion: String,
    val currency: String,
    val serviceLevel: PricingQuoteServiceLevel,
    val pricingZone: PricingQuoteZone,
    val origin: PricingQuoteLocationSnapshot,
    val destination: PricingQuoteLocationSnapshot,
    val actualWeightKg: BigDecimal,
    val volumetricWeightKg: BigDecimal,
    val chargeableWeightKg: BigDecimal,
    val parcelCount: Int,
    val parcels: List<PricingQuoteParcelSnapshot>,
    val codAmount: Long,
    val declaredValue: Long,
    val fees: PricingQuoteFeeSnapshot,
    val estimatedMinDays: Int,
    val estimatedMaxDays: Int,
    val createdAt: Instant,
    val expiresAt: Instant,
)
