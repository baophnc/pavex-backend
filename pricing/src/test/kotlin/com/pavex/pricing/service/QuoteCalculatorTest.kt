package com.pavex.pricing.service

import com.pavex.pricing.dto.CreatePricingParcelRequest
import com.pavex.pricing.entity.CoverageTier
import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.RatePlanStatus
import com.pavex.pricing.entity.RateRule
import com.pavex.pricing.entity.ServiceLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class QuoteCalculatorTest {
    private val calculator = QuoteCalculator()

    @Test
    fun `calculates two compatible customer options`() {
        val plan = demoPlan()
        val parcels = listOf(
            CreatePricingParcelRequest(
                weightKg = BigDecimal("2.10"),
                lengthCm = BigDecimal("20"),
                widthCm = BigDecimal("20"),
                heightCm = BigDecimal("20"),
            ),
        )
        val (metrics, weights) = calculator.validateAndMeasure(parcels, plan, codAmount = 2_000_000, declaredValue = 2_000_000)
        assertEquals(BigDecimal("2.50"), weights.chargeableWeightKg)
        assertEquals(1, metrics.size)

        val options = calculator.price(
            plan = plan,
            zone = PricingZone.CROSS_REGION,
            chargeableWeightKg = weights.chargeableWeightKg,
            originTier = CoverageTier.STANDARD,
            destinationTier = CoverageTier.REMOTE,
            codAmount = 2_000_000,
            declaredValue = 2_000_000,
        )

        assertEquals(listOf(ServiceLevel.STANDARD, ServiceLevel.EXPRESS), options.map { it.serviceLevel })
        assertEquals(6_000, options[0].additionalWeightFee)
        assertEquals(8_000, options[0].areaSurcharge)
        assertEquals(5_000, options[0].codFee)
        assertEquals(5_000, options[0].insuranceFee)
        assertEquals(54_000, options[0].total)
        assertEquals(3, options[0].estimatedMinDays)
        assertEquals(4, options[0].estimatedMaxDays)
    }

    @Test
    fun `island surcharge is percentage of transport and rounded up`() {
        val plan = demoPlan()
        val options = calculator.price(
            plan = plan,
            zone = PricingZone.SAME_REGION,
            chargeableWeightKg = BigDecimal("1.00"),
            originTier = CoverageTier.STANDARD,
            destinationTier = CoverageTier.ISLAND,
            codAmount = 0,
            declaredValue = 0,
        )

        assertEquals(5_000, options[0].areaSurcharge)
        assertEquals(30_000, options[0].total)
        assertEquals(3, options[0].estimatedMinDays)
        assertEquals(4, options[0].estimatedMaxDays)
    }

    private fun demoPlan(): RatePlan {
        val plan = RatePlan(
            code = "TEST",
            revision = 1,
            name = "Test",
            status = RatePlanStatus.ACTIVE,
            quoteTtlMinutes = 30,
            volumetricDivisor = BigDecimal("5000.00"),
            remoteEndpointSurcharge = 8_000,
            islandTransportRate = BigDecimal("0.200000"),
            codFreeThreshold = 1_000_000,
            codRate = BigDecimal("0.005000"),
            codMinimumFee = 5_000,
            maximumCod = 20_000_000,
            insuranceFreeThreshold = 1_000_000,
            insuranceRate = BigDecimal("0.005000"),
            maximumDeclaredValue = 20_000_000,
            maxParcelWeightKg = BigDecimal("30.00"),
            maxShipmentChargeableWeightKg = BigDecimal("30.00"),
            maxDimensionCm = BigDecimal("200.00"),
        )
        plan.rules += listOf(
            rule(plan, ServiceLevel.STANDARD, PricingZone.SAME_REGION, 25_000, 5_000, 1, 2),
            rule(plan, ServiceLevel.EXPRESS, PricingZone.SAME_REGION, 38_000, 7_000, 1, 2),
            rule(plan, ServiceLevel.STANDARD, PricingZone.CROSS_REGION, 30_000, 6_000, 2, 3),
            rule(plan, ServiceLevel.EXPRESS, PricingZone.CROSS_REGION, 50_000, 9_000, 1, 2),
        )
        return plan
    }

    private fun rule(
        plan: RatePlan,
        level: ServiceLevel,
        zone: PricingZone,
        base: Long,
        stepFee: Long,
        minDays: Int,
        maxDays: Int,
    ) = RateRule(
        ratePlan = plan,
        serviceLevel = level,
        pricingZone = zone,
        baseFee = base,
        includedWeightKg = BigDecimal("2.00"),
        additionalWeightStepKg = BigDecimal("0.50"),
        additionalWeightStepFee = stepFee,
        estimatedMinDays = minDays,
        estimatedMaxDays = maxDays,
    )
}
