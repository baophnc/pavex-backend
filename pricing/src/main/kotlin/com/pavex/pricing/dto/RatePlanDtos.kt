package com.pavex.pricing.dto

import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.RatePlanStatus
import com.pavex.pricing.entity.RateRule
import com.pavex.pricing.entity.ServiceLevel
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private const val MONEY_MAX = 1000000000L

data class CreateRatePlanRequest(
    @field:NotBlank
    @field:Size(max = 60)
    @field:Pattern(regexp = "^[A-Za-z0-9._-]+$")
    val code: String,
    @field:Min(1)
    val revision: Int = 1,
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,
    val effectiveFrom: Instant? = null,
    val effectiveTo: Instant? = null,
    @field:Min(1) @field:Max(240)
    val quoteTtlMinutes: Int = 30,
    @field:DecimalMin("100.00") @field:DecimalMax("100000.00")
    val volumetricDivisor: BigDecimal = BigDecimal("5000.00"),
    @field:Min(0) @field:Max(MONEY_MAX)
    val remoteEndpointSurcharge: Long = 0,
    @field:DecimalMin("0.000000") @field:DecimalMax("1.000000")
    val islandTransportRate: BigDecimal = BigDecimal.ZERO,
    @field:Min(0) @field:Max(MONEY_MAX)
    val codFreeThreshold: Long = 0,
    @field:DecimalMin("0.000000") @field:DecimalMax("1.000000")
    val codRate: BigDecimal = BigDecimal.ZERO,
    @field:Min(0) @field:Max(MONEY_MAX)
    val codMinimumFee: Long = 0,
    @field:Min(0) @field:Max(MONEY_MAX)
    val maximumCod: Long = 20_000_000,
    @field:Min(0) @field:Max(MONEY_MAX)
    val insuranceFreeThreshold: Long = 0,
    @field:DecimalMin("0.000000") @field:DecimalMax("1.000000")
    val insuranceRate: BigDecimal = BigDecimal.ZERO,
    @field:Min(0) @field:Max(MONEY_MAX)
    val maximumDeclaredValue: Long = 20_000_000,
    @field:DecimalMin("0.01") @field:DecimalMax("1000.00")
    val maxParcelWeightKg: BigDecimal = BigDecimal("30.00"),
    @field:DecimalMin("0.01") @field:DecimalMax("10000.00")
    val maxShipmentChargeableWeightKg: BigDecimal = BigDecimal("30.00"),
    @field:DecimalMin("1.00") @field:DecimalMax("1000.00")
    val maxDimensionCm: BigDecimal = BigDecimal("200.00"),
)

data class UpdateRatePlanRequest(
    @field:NotBlank @field:Size(max = 200)
    val name: String,
    val effectiveFrom: Instant? = null,
    val effectiveTo: Instant? = null,
    @field:Min(1) @field:Max(240)
    val quoteTtlMinutes: Int,
    @field:DecimalMin("100.00") @field:DecimalMax("100000.00")
    val volumetricDivisor: BigDecimal,
    @field:Min(0) @field:Max(MONEY_MAX)
    val remoteEndpointSurcharge: Long,
    @field:DecimalMin("0.000000") @field:DecimalMax("1.000000")
    val islandTransportRate: BigDecimal,
    @field:Min(0) @field:Max(MONEY_MAX)
    val codFreeThreshold: Long,
    @field:DecimalMin("0.000000") @field:DecimalMax("1.000000")
    val codRate: BigDecimal,
    @field:Min(0) @field:Max(MONEY_MAX)
    val codMinimumFee: Long,
    @field:Min(0) @field:Max(MONEY_MAX)
    val maximumCod: Long,
    @field:Min(0) @field:Max(MONEY_MAX)
    val insuranceFreeThreshold: Long,
    @field:DecimalMin("0.000000") @field:DecimalMax("1.000000")
    val insuranceRate: BigDecimal,
    @field:Min(0) @field:Max(MONEY_MAX)
    val maximumDeclaredValue: Long,
    @field:DecimalMin("0.01") @field:DecimalMax("1000.00")
    val maxParcelWeightKg: BigDecimal,
    @field:DecimalMin("0.01") @field:DecimalMax("10000.00")
    val maxShipmentChargeableWeightKg: BigDecimal,
    @field:DecimalMin("1.00") @field:DecimalMax("1000.00")
    val maxDimensionCm: BigDecimal,
)

data class UpsertRateRuleRequest(
    @field:Min(0) @field:Max(MONEY_MAX)
    val baseFee: Long,
    @field:DecimalMin("0.00") @field:DecimalMax("10000.00")
    val includedWeightKg: BigDecimal,
    @field:DecimalMin("0.01") @field:DecimalMax("1000.00")
    val additionalWeightStepKg: BigDecimal = BigDecimal("0.50"),
    @field:Min(0) @field:Max(MONEY_MAX)
    val additionalWeightStepFee: Long,
    @field:Min(0) @field:Max(365)
    val estimatedMinDays: Int,
    @field:Min(0) @field:Max(365)
    val estimatedMaxDays: Int,
)

data class RateRuleResponse(
    val id: UUID,
    val serviceLevel: ServiceLevel,
    val pricingZone: PricingZone,
    val baseFee: Long,
    val includedWeightKg: BigDecimal,
    val additionalWeightStepKg: BigDecimal,
    val additionalWeightStepFee: Long,
    val estimatedMinDays: Int,
    val estimatedMaxDays: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(rule: RateRule) = RateRuleResponse(
            rule.id,
            rule.serviceLevel,
            rule.pricingZone,
            rule.baseFee,
            rule.includedWeightKg,
            rule.additionalWeightStepKg,
            rule.additionalWeightStepFee,
            rule.estimatedMinDays,
            rule.estimatedMaxDays,
            rule.createdAt,
            rule.updatedAt,
        )
    }
}

data class RatePlanResponse(
    val id: UUID,
    val code: String,
    val revision: Int,
    val name: String,
    val currency: String,
    val status: RatePlanStatus,
    val effectiveFrom: Instant?,
    val effectiveTo: Instant?,
    val quoteTtlMinutes: Int,
    val volumetricDivisor: BigDecimal,
    val remoteEndpointSurcharge: Long,
    val islandTransportRate: BigDecimal,
    val codFreeThreshold: Long,
    val codRate: BigDecimal,
    val codMinimumFee: Long,
    val maximumCod: Long,
    val insuranceFreeThreshold: Long,
    val insuranceRate: BigDecimal,
    val maximumDeclaredValue: Long,
    val maxParcelWeightKg: BigDecimal,
    val maxShipmentChargeableWeightKg: BigDecimal,
    val maxDimensionCm: BigDecimal,
    val ruleCount: Int,
    val rules: List<RateRuleResponse>,
    val createdBy: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val activatedBy: UUID?,
    val activatedAt: Instant?,
    val retiredBy: UUID?,
    val retiredAt: Instant?,
    val version: Long,
) {
    companion object {
        fun from(plan: RatePlan): RatePlanResponse {
            val rules = plan.rules
                .sortedWith(compareBy<RateRule> { it.serviceLevel.name }.thenBy { it.pricingZone.name })
                .map(RateRuleResponse::from)
            return RatePlanResponse(
                plan.id,
                plan.code,
                plan.revision,
                plan.name,
                plan.currency,
                plan.status,
                plan.effectiveFrom,
                plan.effectiveTo,
                plan.quoteTtlMinutes,
                plan.volumetricDivisor,
                plan.remoteEndpointSurcharge,
                plan.islandTransportRate,
                plan.codFreeThreshold,
                plan.codRate,
                plan.codMinimumFee,
                plan.maximumCod,
                plan.insuranceFreeThreshold,
                plan.insuranceRate,
                plan.maximumDeclaredValue,
                plan.maxParcelWeightKg,
                plan.maxShipmentChargeableWeightKg,
                plan.maxDimensionCm,
                rules.size,
                rules,
                plan.createdBy,
                plan.createdAt,
                plan.updatedAt,
                plan.activatedBy,
                plan.activatedAt,
                plan.retiredBy,
                plan.retiredAt,
                plan.version,
            )
        }
    }
}
