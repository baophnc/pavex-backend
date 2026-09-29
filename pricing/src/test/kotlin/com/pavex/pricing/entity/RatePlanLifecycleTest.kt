package com.pavex.pricing.entity

import com.pavex.pricing.exception.ConflictException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `a due hand-over ends and retires the replaced plan`() {
        val plan = activePlan(Instant.parse("2026-08-01T00:00:00Z"))
        val actor = UUID.randomUUID()
        val now = Instant.parse("2026-09-25T05:00:00Z")

        plan.supersede(now, actor, now)

        assertEquals(RatePlanStatus.RETIRED, plan.status)
        assertEquals(now, plan.effectiveTo)
        assertEquals(actor, plan.retiredBy)
    }

    @Test
    fun `a scheduled hand-over keeps the replaced plan active until then`() {
        val plan = activePlan(Instant.parse("2026-08-01T00:00:00Z"))
        val now = Instant.parse("2026-09-25T05:00:00Z")
        val handOverAt = Instant.parse("2026-10-01T00:00:00Z")

        plan.supersede(handOverAt, UUID.randomUUID(), now)

        assertEquals(RatePlanStatus.ACTIVE, plan.status)
        assertEquals(handOverAt, plan.effectiveTo)
        assertNull(plan.retiredAt)
    }

    @Test
    fun `a plan cannot be replaced at or before its own start`() {
        val start = Instant.parse("2026-10-01T00:00:00Z")
        val plan = activePlan(start)

        assertThrows(ConflictException::class.java) {
            plan.supersede(start, UUID.randomUUID(), Instant.parse("2026-09-25T05:00:00Z"))
        }
        assertEquals(RatePlanStatus.ACTIVE, plan.status)
        assertNull(plan.effectiveTo)
    }

    private fun activePlan(effectiveFrom: Instant): RatePlan {
        val plan = RatePlan(code = "DOMESTIC", revision = 1, name = "Domestic r1", effectiveFrom = effectiveFrom)
        completeRules(plan)
        plan.activate(UUID.randomUUID(), effectiveFrom)
        return plan
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
