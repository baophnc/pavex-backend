package com.pavex.pricing.entity

import com.pavex.pricing.exception.ConflictException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RatePlanTest {
    @Test
    fun `draft rule upsert replaces same service level and zone`() {
        val plan = RatePlan(code = "DRAFT-1", name = "Draft")

        plan.upsertRule(
            ServiceLevel.STANDARD,
            PricingZone.SAME_REGION,
            25_000,
            BigDecimal("2.00"),
            BigDecimal("0.50"),
            5_000,
            1,
            2,
        )
        plan.upsertRule(
            ServiceLevel.STANDARD,
            PricingZone.SAME_REGION,
            28_000,
            BigDecimal("2.00"),
            BigDecimal("0.50"),
            6_000,
            1,
            2,
        )

        assertEquals(1, plan.rules.size)
        assertEquals(28_000, plan.rules.single().baseFee)
        assertEquals(6_000, plan.rules.single().additionalWeightStepFee)
    }

    @Test
    fun `active rate plan is immutable`() {
        val plan = RatePlan(code = "ACTIVE-1", name = "Active", status = RatePlanStatus.ACTIVE)

        assertThrows(ConflictException::class.java) {
            plan.upsertRule(
                ServiceLevel.EXPRESS,
                PricingZone.CROSS_REGION,
                50_000,
                BigDecimal("2.00"),
                BigDecimal("0.50"),
                9_000,
                1,
                2,
            )
        }
    }
}
