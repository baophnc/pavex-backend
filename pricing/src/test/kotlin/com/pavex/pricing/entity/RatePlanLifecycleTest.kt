package com.pavex.pricing.entity

import com.pavex.pricing.exception.ConflictException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RatePlanLifecycleTest {
    @Test
    fun `active plan becomes immutable and can be retired`() {
        val plan = RatePlan(code = "DOMESTIC", revision = 2, name = "Domestic r2")
        completeRules(plan)
        val actor = UUID.randomUUID()
        val activatedAt = Instant.parse("2026-08-29T04:00:00Z")

        plan.activate(actor, activatedAt)

        assertEquals(RatePlanStatus.ACTIVE, plan.status)
        assertEquals(activatedAt, plan.effectiveFrom)
        assertEquals(actor, plan.activatedBy)
        assertNotNull(plan.activatedAt)
        assertThrows(ConflictException::class.java) {
            plan.updateDraft(
                "changed",
                plan.effectiveFrom,
                null,
                30,
                BigDecimal("5000"),
                0,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                20_000_000,
                0,
                BigDecimal.ZERO,
                20_000_000,
                BigDecimal("30"),
                BigDecimal("30"),
                BigDecimal("200"),
            )
        }

        val retiredAt = activatedAt.plusSeconds(60)
        plan.retire(actor, retiredAt)
        assertEquals(RatePlanStatus.RETIRED, plan.status)
        assertEquals(retiredAt, plan.effectiveTo)
        assertEquals(actor, plan.retiredBy)
    }

    private fun completeRules(plan: RatePlan) {
        ServiceLevel.entries.forEach { service ->
            PricingZone.entries.forEach { zone ->
                plan.rules += RateRule(
                    ratePlan = plan,
                    serviceLevel = service,
                    pricingZone = zone,
                    baseFee = 10_000,
                    includedWeightKg = BigDecimal("2.00"),
                    additionalWeightStepKg = BigDecimal("0.50"),
                    additionalWeightStepFee = 1_000,
                    estimatedMinDays = 1,
                    estimatedMaxDays = 2,
                )
            }
        }
    }
}
