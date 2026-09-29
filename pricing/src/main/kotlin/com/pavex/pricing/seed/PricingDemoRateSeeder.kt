package com.pavex.pricing.seed

import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.RatePlanStatus
import com.pavex.pricing.entity.RateRule
import com.pavex.pricing.entity.ServiceLevel
import com.pavex.pricing.repository.RatePlanRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@Component
@Order(300)
@ConditionalOnProperty(prefix = "pricing.seed.demo", name = ["enabled"], havingValue = "true")
class PricingDemoRateSeeder(
    private val ratePlans: RatePlanRepository,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val code = "DEMO-2026-08"
        if (ratePlans.existsByCodeIgnoreCaseAndRevision(code, 1)) {
            log.info("Pricing demo rate plan already exists; seed skipped: {}", code)
            return
        }

        val plan = RatePlan(
            code = code,
            revision = 1,
            name = "Bảng giá demo tháng 08/2026",
            currency = "VND",
            status = RatePlanStatus.ACTIVE,
            effectiveFrom = Instant.parse("2026-08-01T00:00:00Z"),
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
            activatedAt = Instant.parse("2026-08-01T00:00:00Z"),
        )

        plan.rules += listOf(
            rule(plan, ServiceLevel.STANDARD, PricingZone.SAME_PROVINCE, 20_000, 2, 4_000, 1, 2),
            rule(plan, ServiceLevel.STANDARD, PricingZone.SAME_REGION, 25_000, 2, 5_000, 1, 2),
            rule(plan, ServiceLevel.STANDARD, PricingZone.CROSS_REGION, 30_000, 2, 6_000, 2, 3),
            rule(plan, ServiceLevel.EXPRESS, PricingZone.SAME_PROVINCE, 30_000, 2, 6_000, 1, 1),
            rule(plan, ServiceLevel.EXPRESS, PricingZone.SAME_REGION, 38_000, 2, 7_000, 1, 2),
            rule(plan, ServiceLevel.EXPRESS, PricingZone.CROSS_REGION, 50_000, 2, 9_000, 1, 2),
        )

        ratePlans.save(plan)
        log.info("Pricing demo rate catalog ready: code={}, rules={}", code, plan.rules.size)
    }

    private fun rule(
        plan: RatePlan,
        serviceLevel: ServiceLevel,
        zone: PricingZone,
        baseFee: Long,
        includedWeightKg: Long,
        stepFee: Long,
        minDays: Int,
        maxDays: Int,
    ) = RateRule(
        ratePlan = plan,
        serviceLevel = serviceLevel,
        pricingZone = zone,
        baseFee = baseFee,
        includedWeightKg = BigDecimal.valueOf(includedWeightKg),
        additionalWeightStepKg = BigDecimal("0.50"),
        additionalWeightStepFee = stepFee,
        estimatedMinDays = minDays,
        estimatedMaxDays = maxDays,
    )
}
