package com.pavex.pricing.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreatePricingQuoteRequest(
    @field:NotBlank @field:Size(max = 20) val originProvinceCode: String,
    @field:NotBlank @field:Size(max = 20) val originWardCode: String,
    @field:NotBlank @field:Size(max = 20) val destinationProvinceCode: String,
    @field:NotBlank @field:Size(max = 20) val destinationWardCode: String,
    @field:Positive val weightKg: BigDecimal? = null,
    @field:Positive val lengthCm: BigDecimal? = null,
    @field:Positive val widthCm: BigDecimal? = null,
    @field:Positive val heightCm: BigDecimal? = null,
    @field:PositiveOrZero val codAmount: Long = 0,
    @field:PositiveOrZero val declaredValue: Long = 0,
    @field:Valid @field:Size(max = 100) val parcels: List<CreatePricingParcelRequest>? = null,
)

data class CreatePricingParcelRequest(
    @field:Positive val weightKg: BigDecimal,
    @field:Positive val lengthCm: BigDecimal,
    @field:Positive val widthCm: BigDecimal,
    @field:Positive val heightCm: BigDecimal,
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
