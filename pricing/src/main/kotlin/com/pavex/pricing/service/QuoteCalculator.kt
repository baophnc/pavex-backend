package com.pavex.pricing.service

import com.pavex.pricing.dto.CreatePricingParcelRequest
import com.pavex.pricing.entity.CoverageTier
import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.RateRule
import com.pavex.pricing.entity.ServiceLevel
import com.pavex.pricing.exception.InvalidPricingRequestException
import com.pavex.pricing.exception.PricingUnavailableException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class QuoteCalculator {
    data class ParcelMetrics(
        val actualWeightKg: BigDecimal,
        val volumetricWeightKg: BigDecimal,
        val chargeableWeightKg: BigDecimal,
        val lengthCm: BigDecimal,
        val widthCm: BigDecimal,
        val heightCm: BigDecimal,
    )

    data class WeightSummary(
        val actualWeightKg: BigDecimal,
        val volumetricWeightKg: BigDecimal,
        val chargeableWeightKg: BigDecimal,
    )

    data class OptionPrice(
        val serviceLevel: ServiceLevel,
        val baseFee: Long,
        val additionalWeightFee: Long,
        val areaSurcharge: Long,
        val codFee: Long,
        val insuranceFee: Long,
        val total: Long,
        val estimatedMinDays: Int,
        val estimatedMaxDays: Int,
    )

    fun validateAndMeasure(
        parcels: List<CreatePricingParcelRequest>,
        plan: RatePlan,
        codAmount: Long,
        declaredValue: Long,
    ): Pair<List<ParcelMetrics>, WeightSummary> {
        if (parcels.size !in 1..20) {
            throw InvalidPricingRequestException(
                "invalid_parcel_count",
                "A quote must contain between 1 and 20 parcels.",
            )
        }
        if (codAmount < 0 || codAmount > plan.maximumCod) {
            throw InvalidPricingRequestException(
                "invalid_cod_amount",
                "COD amount must be between 0 and ${plan.maximumCod} VND.",
            )
        }
        if (declaredValue < 0 || declaredValue > plan.maximumDeclaredValue) {
            throw InvalidPricingRequestException(
                "invalid_declared_value",
                "Declared value must be between 0 and ${plan.maximumDeclaredValue} VND.",
            )
        }

        val measured = parcels.mapIndexed { index, parcel ->
            validateParcel(index + 1, parcel, plan)
            val rawVolumetric = parcel.lengthCm
                .multiply(parcel.widthCm)
                .multiply(parcel.heightCm)
                .divide(plan.volumetricDivisor, 8, RoundingMode.HALF_UP)
            val volumetric = rawVolumetric.setScale(2, RoundingMode.HALF_UP)
            val chargeable = roundUp(parcel.weightKg.max(rawVolumetric), BigDecimal("0.50"))
                .setScale(2, RoundingMode.UNNECESSARY)
            ParcelMetrics(
                actualWeightKg = parcel.weightKg.setScale(2, RoundingMode.HALF_UP),
                volumetricWeightKg = volumetric,
                chargeableWeightKg = chargeable,
                lengthCm = parcel.lengthCm.setScale(2, RoundingMode.HALF_UP),
                widthCm = parcel.widthCm.setScale(2, RoundingMode.HALF_UP),
                heightCm = parcel.heightCm.setScale(2, RoundingMode.HALF_UP),
            )
        }
        val summary = WeightSummary(
            actualWeightKg = measured.sumOfBigDecimal { it.actualWeightKg },
            volumetricWeightKg = measured.sumOfBigDecimal { it.volumetricWeightKg },
            chargeableWeightKg = measured.sumOfBigDecimal { it.chargeableWeightKg },
        )
        if (summary.chargeableWeightKg > plan.maxShipmentChargeableWeightKg) {
            throw InvalidPricingRequestException(
                "shipment_too_large",
                "Chargeable shipment weight must not exceed ${plan.maxShipmentChargeableWeightKg.stripTrailingZeros().toPlainString()} kg.",
            )
        }
        return measured to summary
    }

    fun price(
        plan: RatePlan,
        zone: PricingZone,
        chargeableWeightKg: BigDecimal,
        originTier: CoverageTier,
        destinationTier: CoverageTier,
        codAmount: Long,
        declaredValue: Long,
    ): List<OptionPrice> =
        ServiceLevel.entries.map { level ->
            val rule = plan.rules.singleOrNull {
                it.serviceLevel == level && it.pricingZone == zone
            } ?: throw PricingUnavailableException(
                "rate_rule_missing",
                "The active rate plan is missing $level/$zone pricing.",
            )
            priceOption(plan, rule, chargeableWeightKg, originTier, destinationTier, codAmount, declaredValue)
        }

    private fun priceOption(
        plan: RatePlan,
        rule: RateRule,
        chargeableWeightKg: BigDecimal,
        originTier: CoverageTier,
        destinationTier: CoverageTier,
        codAmount: Long,
        declaredValue: Long,
    ): OptionPrice {
        val extraWeight = chargeableWeightKg.subtract(rule.includedWeightKg).max(BigDecimal.ZERO)
        val steps = if (extraWeight.signum() == 0) {
            0L
        } else {
            extraWeight.divide(rule.additionalWeightStepKg, 0, RoundingMode.CEILING).longValueExact()
        }
        val additionalWeightFee = Math.multiplyExact(steps, rule.additionalWeightStepFee)
        val transportFee = Math.addExact(rule.baseFee, additionalWeightFee)
        val areaSurcharge = calculateAreaSurcharge(plan, transportFee, originTier, destinationTier)
        val codFee = calculateCodFee(plan, codAmount)
        val insuranceFee = calculateInsuranceFee(plan, declaredValue)
        val totalBeforeRounding = BigDecimal.valueOf(transportFee)
            .add(BigDecimal.valueOf(areaSurcharge))
            .add(BigDecimal.valueOf(codFee))
            .add(BigDecimal.valueOf(insuranceFee))
        val total = roundUpMoney(totalBeforeRounding)
        val etaAdjustment = etaAdjustment(originTier, destinationTier)
        return OptionPrice(
            serviceLevel = rule.serviceLevel,
            baseFee = rule.baseFee,
            additionalWeightFee = additionalWeightFee,
            areaSurcharge = areaSurcharge,
            codFee = codFee,
            insuranceFee = insuranceFee,
            total = total,
            estimatedMinDays = rule.estimatedMinDays + etaAdjustment,
            estimatedMaxDays = rule.estimatedMaxDays + etaAdjustment,
        )
    }

    private fun validateParcel(sequence: Int, parcel: CreatePricingParcelRequest, plan: RatePlan) {
        if (parcel.weightKg <= BigDecimal.ZERO || parcel.weightKg > plan.maxParcelWeightKg) {
            throw InvalidPricingRequestException(
                "invalid_weight",
                "Parcel $sequence weight must be greater than 0 and at most ${plan.maxParcelWeightKg.stripTrailingZeros().toPlainString()} kg.",
            )
        }
        listOf(parcel.lengthCm, parcel.widthCm, parcel.heightCm).forEach { dimension ->
            if (dimension <= BigDecimal.ZERO || dimension > plan.maxDimensionCm) {
                throw InvalidPricingRequestException(
                    "invalid_dimensions",
                    "Every parcel dimension must be greater than 0 and at most ${plan.maxDimensionCm.stripTrailingZeros().toPlainString()} cm.",
                )
            }
        }
    }

    private fun calculateAreaSurcharge(
        plan: RatePlan,
        transportFee: Long,
        originTier: CoverageTier,
        destinationTier: CoverageTier,
    ): Long {
        if (originTier == CoverageTier.ISLAND || destinationTier == CoverageTier.ISLAND) {
            return roundUpMoney(BigDecimal.valueOf(transportFee).multiply(plan.islandTransportRate))
        }
        val remoteEndpoints = listOf(originTier, destinationTier).count { it == CoverageTier.REMOTE }
        return Math.multiplyExact(remoteEndpoints.toLong(), plan.remoteEndpointSurcharge)
    }

    private fun calculateCodFee(plan: RatePlan, codAmount: Long): Long {
        if (codAmount <= plan.codFreeThreshold) return 0
        val variableFee = BigDecimal.valueOf(codAmount - plan.codFreeThreshold).multiply(plan.codRate)
        val fee = variableFee.max(BigDecimal.valueOf(plan.codMinimumFee))
        return roundUpMoney(fee)
    }

    private fun calculateInsuranceFee(plan: RatePlan, declaredValue: Long): Long {
        if (declaredValue <= plan.insuranceFreeThreshold) return 0
        val fee = BigDecimal.valueOf(declaredValue - plan.insuranceFreeThreshold).multiply(plan.insuranceRate)
        return roundUpMoney(fee)
    }

    private fun etaAdjustment(originTier: CoverageTier, destinationTier: CoverageTier): Int = when {
        originTier == CoverageTier.ISLAND || destinationTier == CoverageTier.ISLAND -> 2
        originTier == CoverageTier.REMOTE || destinationTier == CoverageTier.REMOTE -> 1
        else -> 0
    }

    private fun roundUpMoney(value: BigDecimal): Long =
        roundUp(value, BigDecimal("1000")).longValueExact()

    private fun roundUp(value: BigDecimal, step: BigDecimal): BigDecimal =
        value.divide(step, 0, RoundingMode.CEILING).multiply(step)
}

private inline fun <T> Iterable<T>.sumOfBigDecimal(selector: (T) -> BigDecimal): BigDecimal =
    fold(BigDecimal.ZERO) { total, item -> total.add(selector(item)) }
