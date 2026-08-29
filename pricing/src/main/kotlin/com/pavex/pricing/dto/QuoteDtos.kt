package com.pavex.pricing.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreatePricingQuoteRequest(
    val originProvinceCode: String,
    val originWardCode: String,
    val destinationProvinceCode: String,
    val destinationWardCode: String,
    val weightKg: BigDecimal? = null,
    val lengthCm: BigDecimal? = null,
    val widthCm: BigDecimal? = null,
    val heightCm: BigDecimal? = null,
    val codAmount: Long = 0,
    val declaredValue: Long = 0,
    val parcels: List<CreatePricingParcelRequest>? = null,
)

data class CreatePricingParcelRequest(
    val weightKg: BigDecimal,
    val lengthCm: BigDecimal,
    val widthCm: BigDecimal,
    val heightCm: BigDecimal,
)

data class PricingQuoteResponse(
    val requestId: UUID,
    val rateVersion: String,
    val lane: String,
    val actualWeightKg: BigDecimal,
    val volumetricWeightKg: BigDecimal,
    val chargeableWeightKg: BigDecimal,
    val parcelCount: Int,
    val expiresAt: Instant,
    val options: List<PricingOptionResponse>,
)

data class PricingOptionResponse(
    val quoteId: UUID,
    val serviceLevel: String,
    val baseFee: Long,
    val additionalWeightFee: Long,
    val areaSurcharge: Long,
    val codFee: Long,
    val insuranceFee: Long,
    val total: Long,
    val estimatedMinDays: Int,
    val estimatedMaxDays: Int,
)
