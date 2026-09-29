package com.pavex.pricing.service

import com.pavex.pricing.entity.PricingAuditAction
import com.pavex.pricing.entity.PricingAuditLog
import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.RatePlanStatus
import com.pavex.pricing.entity.RateRule
import com.pavex.pricing.entity.ServiceLevel
import com.pavex.pricing.exception.ConflictException
import com.pavex.pricing.repository.PricingAuditLogRepository
import com.pavex.pricing.repository.RatePlanRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RatePlanServiceTest {
    private val now = Instant.parse("2026-09-25T05:00:00Z")
    private val actor = UUID.randomUUID()
    private val plans = linkedMapOf<UUID, RatePlan>()
    private val auditLogs = mutableListOf<PricingAuditLog>()
    private val service = RatePlanService(ratePlanRepository(), PricingAuditService(auditRepository(), JsonMapper.builder().build()))

    @Test
    fun `activating over an active plan without replacing it is refused`() {
        val current = activePlan(Instant.parse("2026-08-01T00:00:00Z"))
        val draft = draftPlan(effectiveFrom = null)

        val error = assertThrows(ConflictException::class.java) {
            service.activate(draft.id, actor, reason = null, now = now)
        }

        assertEquals("active_rate_plan_overlap", error.code)
        assertEquals(RatePlanStatus.ACTIVE, current.status)
        assertNull(current.effectiveTo)
        assertEquals(RatePlanStatus.DRAFT, draft.status)
    }

    @Test
    fun `replacing now hands over in one step without a gap`() {
        val current = activePlan(Instant.parse("2026-08-01T00:00:00Z"))
        val draft = draftPlan(effectiveFrom = null)

        service.activate(draft.id, actor, "Giá mới Q4", replaceActivePlan = true, now = now)

        assertEquals(RatePlanStatus.RETIRED, current.status)
        assertEquals(now, current.effectiveTo)
        assertEquals(RatePlanStatus.ACTIVE, draft.status)
        assertEquals(now, draft.effectiveFrom)
        val superseded = auditLogs.single { it.entityId == current.id }
        assertEquals(PricingAuditAction.SUPERSEDED, superseded.action)
        assertEquals("Thay thế bởi DOMESTIC-r2. Giá mới Q4", superseded.reason)
        assertEquals(PricingAuditAction.ACTIVATED, auditLogs.single { it.entityId == draft.id }.action)
    }

    @Test
    fun `a scheduled replacement keeps the current plan until the hand-over`() {
        val current = activePlan(Instant.parse("2026-08-01T00:00:00Z"))
        val handOverAt = Instant.parse("2026-10-01T00:00:00Z")
        val draft = draftPlan(effectiveFrom = handOverAt)

        service.activate(draft.id, actor, reason = null, replaceActivePlan = true, now = now)

        assertEquals(RatePlanStatus.ACTIVE, current.status)
        assertEquals(handOverAt, current.effectiveTo)
        assertEquals(RatePlanStatus.ACTIVE, draft.status)
        assertEquals(handOverAt, draft.effectiveFrom)
    }

    @Test
    fun `a replacement never backdates the hand-over`() {
        val current = activePlan(Instant.parse("2026-08-01T00:00:00Z"))
        val draft = draftPlan(effectiveFrom = now.minusSeconds(3_600))

        service.activate(draft.id, actor, reason = null, replaceActivePlan = true, now = now)

        assertEquals(now, current.effectiveTo)
        assertEquals(now, draft.effectiveFrom)
    }

    @Test
    fun `a replacement that ends before the current plan would leave a gap and is refused`() {
        val current = activePlan(Instant.parse("2026-08-01T00:00:00Z"))
        val draft = draftPlan(effectiveFrom = null, effectiveTo = now.plusSeconds(7 * 86_400))

        val error = assertThrows(ConflictException::class.java) {
            service.activate(draft.id, actor, reason = null, replaceActivePlan = true, now = now)
        }

        assertEquals("rate_plan_replacement_gap", error.code)
        assertEquals(RatePlanStatus.ACTIVE, current.status)
        assertNull(current.effectiveTo)
        assertEquals(RatePlanStatus.DRAFT, draft.status)
        assertTrue(auditLogs.isEmpty())
    }

    @Test
    fun `a plan scheduled to start after the replacement cannot be replaced`() {
        val scheduled = activePlan(Instant.parse("2026-10-01T00:00:00Z"))
        val draft = draftPlan(effectiveFrom = null)

        val error = assertThrows(ConflictException::class.java) {
            service.activate(draft.id, actor, reason = null, replaceActivePlan = true, now = now)
        }

        assertEquals("rate_plan_replacement_starts_later", error.code)
        assertNull(scheduled.effectiveTo)
    }

    @Test
    fun `activation never starts in the past, so a plan that already ended is no conflict`() {
        val ended = activePlan(Instant.parse("2026-08-01T00:00:00Z"))
        ended.effectiveTo = now.minusSeconds(86_400)
        val draft = draftPlan(effectiveFrom = now.minusSeconds(3 * 86_400))

        service.activate(draft.id, actor, reason = null, now = now)

        assertEquals(RatePlanStatus.ACTIVE, draft.status)
        assertEquals(now, draft.effectiveFrom)
        assertEquals(RatePlanStatus.ACTIVE, ended.status)
    }

    @Test
    fun `a later scheduled plan is reported before any coverage gap`() {
        activePlan(Instant.parse("2026-08-01T00:00:00Z")).effectiveTo = Instant.parse("2026-10-01T00:00:00Z")
        activePlan(Instant.parse("2026-10-01T00:00:00Z"))
        val draft = draftPlan(effectiveFrom = null, effectiveTo = Instant.parse("2026-10-15T00:00:00Z"))

        val error = assertThrows(ConflictException::class.java) {
            service.activate(draft.id, actor, reason = null, replaceActivePlan = true, now = now)
        }

        assertEquals("rate_plan_replacement_starts_later", error.code)
        assertTrue(auditLogs.isEmpty())
    }

    private fun activePlan(effectiveFrom: Instant): RatePlan {
        val plan = RatePlan(code = "DOMESTIC", revision = 1, name = "Domestic r1", effectiveFrom = effectiveFrom)
        completeRules(plan)
        plan.activate(UUID.randomUUID(), effectiveFrom)
        plans[plan.id] = plan
        return plan
    }

    private fun draftPlan(effectiveFrom: Instant?, effectiveTo: Instant? = null): RatePlan {
        val plan = RatePlan(
            code = "DOMESTIC",
            revision = 2,
            name = "Domestic r2",
            effectiveFrom = effectiveFrom,
            effectiveTo = effectiveTo,
        )
        completeRules(plan)
        plans[plan.id] = plan
        return plan
    }

    private fun completeRules(plan: RatePlan) {
        ServiceLevel.entries.forEach { service ->
            PricingZone.entries.forEach { zone ->
                plan.rules += RateRule(
                    ratePlan = plan,
                    serviceLevel = service,
                    pricingZone = zone,
                    baseFee = 20_000,
                    includedWeightKg = BigDecimal("2.00"),
                    additionalWeightStepKg = BigDecimal("0.50"),
                    additionalWeightStepFee = 2_000,
                    estimatedMinDays = 1,
                    estimatedMaxDays = 2,
                )
            }
        }
    }

    /** Mirrors RatePlanRepository.findOverlappingActive over the in-memory plans. */
    private fun ratePlanRepository(): RatePlanRepository = proxy { name, args ->
        when (name) {
            "findWithRulesById" -> plans[args[0] as UUID]
            "save" -> args[0]
            "findOverlappingActive" -> {
                val status = args[0] as RatePlanStatus
                val excludedId = args[1] as UUID
                val from = args[2] as Instant
                val to = args[3] as Instant?
                plans.values.filter {
                    it.status == status &&
                        it.id != excludedId &&
                        (it.effectiveTo == null || it.effectiveTo!!.isAfter(from)) &&
                        (to == null || it.effectiveFrom == null || it.effectiveFrom!!.isBefore(to))
                }
            }
            else -> error("Unexpected RatePlanRepository.$name")
        }
    }

    private fun auditRepository(): PricingAuditLogRepository = proxy { name, args ->
        when (name) {
            "save" -> (args[0] as PricingAuditLog).also(auditLogs::add)
            else -> error("Unexpected PricingAuditLogRepository.$name")
        }
    }

    private inline fun <reified T> proxy(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "toString" -> T::class.java.simpleName
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.get(0)
                else -> handler(method.name, args ?: emptyArray())
            }
        } as T
}
