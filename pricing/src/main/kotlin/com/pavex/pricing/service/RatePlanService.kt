package com.pavex.pricing.service

import com.pavex.pricing.dto.CreateRatePlanRequest
import com.pavex.pricing.dto.CreateRatePlanRevisionRequest
import com.pavex.pricing.dto.RatePlanResponse
import com.pavex.pricing.dto.RateRuleResponse
import com.pavex.pricing.dto.UpdateRatePlanRequest
import com.pavex.pricing.dto.UpsertRateRuleRequest
import com.pavex.pricing.entity.PricingAuditAction
import com.pavex.pricing.entity.PricingAuditEntityType
import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.RatePlanStatus
import com.pavex.pricing.entity.RateRule
import com.pavex.pricing.entity.ServiceLevel
import com.pavex.pricing.exception.ConflictException
import com.pavex.pricing.exception.InvalidPricingRequestException
import com.pavex.pricing.exception.NotFoundException
import com.pavex.pricing.repository.RatePlanRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class RatePlanService(
    private val ratePlans: RatePlanRepository,
    private val audit: PricingAuditService,
) {
    @Transactional(readOnly = true)
    fun list(status: RatePlanStatus?): List<RatePlanResponse> =
        (status?.let(ratePlans::findAllByStatusOrderByUpdatedAtDesc)
            ?: ratePlans.findAllByOrderByUpdatedAtDesc())
            .map(RatePlanResponse::from)

    @Transactional(readOnly = true)
    fun get(id: UUID): RatePlanResponse = RatePlanResponse.from(find(id))

    @Transactional(readOnly = true)
    fun current(at: Instant): RatePlanResponse = RatePlanResponse.from(currentEntity(at))

    @Transactional(readOnly = true)
    fun currentEntity(at: Instant): RatePlan {
        val matches = ratePlans.findActiveAt(RatePlanStatus.ACTIVE, at)
        if (matches.isEmpty()) {
            throw NotFoundException("active_rate_plan_not_found", "No active rate plan covers the requested time.")
        }
        if (matches.size > 1) {
            throw ConflictException("multiple_active_rate_plans", "More than one active rate plan covers the requested time.")
        }
        return matches.single()
    }

    @Transactional
    fun create(request: CreateRatePlanRequest, actorUserId: UUID): RatePlanResponse {
        validateWindow(request.effectiveFrom, request.effectiveTo)
        validateWeightLimits(request.maxParcelWeightKg, request.maxShipmentChargeableWeightKg)
        validateMonetaryLimits(request.codFreeThreshold, request.maximumCod, request.insuranceFreeThreshold, request.maximumDeclaredValue)
        val code = request.code.trim().uppercase()
        if (ratePlans.existsByCodeIgnoreCaseAndRevision(code, request.revision)) {
            throw ConflictException("rate_plan_revision_exists", "This rate plan code and revision already exist.")
        }
        val plan = RatePlan(
            code = code,
            revision = request.revision,
            name = request.name.trim(),
            currency = "VND",
            status = RatePlanStatus.DRAFT,
            effectiveFrom = request.effectiveFrom,
            effectiveTo = request.effectiveTo,
            quoteTtlMinutes = request.quoteTtlMinutes,
            volumetricDivisor = request.volumetricDivisor,
            remoteEndpointSurcharge = request.remoteEndpointSurcharge,
            islandTransportRate = request.islandTransportRate,
            codFreeThreshold = request.codFreeThreshold,
            codRate = request.codRate,
            codMinimumFee = request.codMinimumFee,
            maximumCod = request.maximumCod,
            insuranceFreeThreshold = request.insuranceFreeThreshold,
            insuranceRate = request.insuranceRate,
            maximumDeclaredValue = request.maximumDeclaredValue,
            maxParcelWeightKg = request.maxParcelWeightKg,
            maxShipmentChargeableWeightKg = request.maxShipmentChargeableWeightKg,
            maxDimensionCm = request.maxDimensionCm,
            createdBy = actorUserId,
        )
        val saved = ratePlans.save(plan)
        val response = RatePlanResponse.from(saved)
        audit.record(PricingAuditEntityType.RATE_PLAN, saved.id, PricingAuditAction.CREATED, actorUserId, after = response)
        return response
    }

    @Transactional
    fun createRevision(
        sourceId: UUID,
        request: CreateRatePlanRevisionRequest,
        actorUserId: UUID,
    ): RatePlanResponse {
        val source = find(sourceId)
        validateWindow(request.effectiveFrom, request.effectiveTo)
        val revision = ratePlans.findMaxRevision(source.code) + 1
        val copy = RatePlan(
            code = source.code,
            revision = revision,
            name = request.name?.trim()?.takeIf { it.isNotBlank() } ?: source.name,
            currency = source.currency,
            status = RatePlanStatus.DRAFT,
            effectiveFrom = request.effectiveFrom,
            effectiveTo = request.effectiveTo,
            quoteTtlMinutes = source.quoteTtlMinutes,
            volumetricDivisor = source.volumetricDivisor,
            remoteEndpointSurcharge = source.remoteEndpointSurcharge,
            islandTransportRate = source.islandTransportRate,
            codFreeThreshold = source.codFreeThreshold,
            codRate = source.codRate,
            codMinimumFee = source.codMinimumFee,
            maximumCod = source.maximumCod,
            insuranceFreeThreshold = source.insuranceFreeThreshold,
            insuranceRate = source.insuranceRate,
            maximumDeclaredValue = source.maximumDeclaredValue,
            maxParcelWeightKg = source.maxParcelWeightKg,
            maxShipmentChargeableWeightKg = source.maxShipmentChargeableWeightKg,
            maxDimensionCm = source.maxDimensionCm,
            createdBy = actorUserId,
        )
        source.rules.forEach { rule ->
            copy.rules += RateRule(
                ratePlan = copy,
                serviceLevel = rule.serviceLevel,
                pricingZone = rule.pricingZone,
                baseFee = rule.baseFee,
                includedWeightKg = rule.includedWeightKg,
                additionalWeightStepKg = rule.additionalWeightStepKg,
                additionalWeightStepFee = rule.additionalWeightStepFee,
                estimatedMinDays = rule.estimatedMinDays,
                estimatedMaxDays = rule.estimatedMaxDays,
            )
        }
        val saved = ratePlans.save(copy)
        val response = RatePlanResponse.from(saved)
        audit.record(
            PricingAuditEntityType.RATE_PLAN,
            saved.id,
            PricingAuditAction.REVISION_CREATED,
            actorUserId,
            before = RatePlanResponse.from(source),
            after = response,
            reason = request.reason,
        )
        return response
    }

    @Transactional
    fun update(id: UUID, request: UpdateRatePlanRequest, actorUserId: UUID): RatePlanResponse {
        validateWindow(request.effectiveFrom, request.effectiveTo)
        validateWeightLimits(request.maxParcelWeightKg, request.maxShipmentChargeableWeightKg)
        validateMonetaryLimits(request.codFreeThreshold, request.maximumCod, request.insuranceFreeThreshold, request.maximumDeclaredValue)
        val plan = find(id)
        val before = RatePlanResponse.from(plan)
        plan.updateDraft(
            request.name,
            request.effectiveFrom,
            request.effectiveTo,
            request.quoteTtlMinutes,
            request.volumetricDivisor,
            request.remoteEndpointSurcharge,
            request.islandTransportRate,
            request.codFreeThreshold,
            request.codRate,
            request.codMinimumFee,
            request.maximumCod,
            request.insuranceFreeThreshold,
            request.insuranceRate,
            request.maximumDeclaredValue,
            request.maxParcelWeightKg,
            request.maxShipmentChargeableWeightKg,
            request.maxDimensionCm,
        )
        val response = RatePlanResponse.from(ratePlans.save(plan))
        audit.record(PricingAuditEntityType.RATE_PLAN, id, PricingAuditAction.UPDATED, actorUserId, before, response)
        return response
    }

    @Transactional
    fun upsertRule(
        id: UUID,
        serviceLevel: ServiceLevel,
        pricingZone: PricingZone,
        request: UpsertRateRuleRequest,
        actorUserId: UUID,
    ): RatePlanResponse {
        if (request.estimatedMaxDays < request.estimatedMinDays) {
            throw InvalidPricingRequestException(
                "invalid_eta_range",
                "Estimated maximum days must be greater than or equal to estimated minimum days.",
            )
        }
        val plan = find(id)
        val existing = plan.rules.firstOrNull { it.serviceLevel == serviceLevel && it.pricingZone == pricingZone }
        val before = existing?.let(RateRuleResponse::from)
        val rule = plan.upsertRule(
            serviceLevel,
            pricingZone,
            request.baseFee,
            request.includedWeightKg,
            request.additionalWeightStepKg,
            request.additionalWeightStepFee,
            request.estimatedMinDays,
            request.estimatedMaxDays,
        )
        val response = RatePlanResponse.from(ratePlans.save(plan))
        audit.record(
            PricingAuditEntityType.RATE_RULE,
            rule.id,
            PricingAuditAction.RULE_UPSERTED,
            actorUserId,
            before = before,
            after = RateRuleResponse.from(rule),
        )
        return response
    }

    @Transactional
    fun deleteRule(
        id: UUID,
        serviceLevel: ServiceLevel,
        pricingZone: PricingZone,
        actorUserId: UUID,
    ): RatePlanResponse {
        val plan = find(id)
        val existing = plan.rules.firstOrNull { it.serviceLevel == serviceLevel && it.pricingZone == pricingZone }
            ?: throw NotFoundException("rate_rule_not_found", "Rate rule was not found.")
        val before = RateRuleResponse.from(existing)
        plan.removeRule(serviceLevel, pricingZone)
        val response = RatePlanResponse.from(ratePlans.save(plan))
        audit.record(
            PricingAuditEntityType.RATE_RULE,
            existing.id,
            PricingAuditAction.RULE_DELETED,
            actorUserId,
            before = before,
        )
        return response
    }

    @Transactional
    fun activate(id: UUID, actorUserId: UUID, reason: String?, now: Instant = Instant.now()): RatePlanResponse {
        val plan = find(id)
        plan.requireDraft()
        validateCompleteRules(plan)
        val effectiveFrom = plan.effectiveFrom ?: now
        validateWindow(effectiveFrom, plan.effectiveTo)
        val overlaps = ratePlans.findOverlappingActive(RatePlanStatus.ACTIVE, plan.id, effectiveFrom, plan.effectiveTo)
        if (overlaps.isNotEmpty()) {
            val conflicting = overlaps.joinToString { "${it.code}-r${it.revision}" }
            throw ConflictException(
                "active_rate_plan_overlap",
                "This rate plan overlaps an active rate plan: $conflicting. Retire or end the existing plan first.",
            )
        }
        val before = RatePlanResponse.from(plan)
        plan.activate(actorUserId, now)
        val response = RatePlanResponse.from(ratePlans.save(plan))
        audit.record(
            PricingAuditEntityType.RATE_PLAN,
            id,
            PricingAuditAction.ACTIVATED,
            actorUserId,
            before,
            response,
            reason,
        )
        return response
    }

    @Transactional
    fun retire(id: UUID, actorUserId: UUID, reason: String?, now: Instant = Instant.now()): RatePlanResponse {
        val plan = find(id)
        val before = RatePlanResponse.from(plan)
        plan.retire(actorUserId, now)
        val response = RatePlanResponse.from(ratePlans.save(plan))
        audit.record(
            PricingAuditEntityType.RATE_PLAN,
            id,
            PricingAuditAction.RETIRED,
            actorUserId,
            before,
            response,
            reason,
        )
        return response
    }

    @Transactional
    fun deleteDraft(id: UUID, actorUserId: UUID, reason: String?) {
        val plan = find(id)
        plan.requireDraft()
        val before = RatePlanResponse.from(plan)
        ratePlans.delete(plan)
        audit.record(
            PricingAuditEntityType.RATE_PLAN,
            id,
            PricingAuditAction.DELETED,
            actorUserId,
            before = before,
            reason = reason,
        )
    }

    fun find(id: UUID): RatePlan = ratePlans.findWithRulesById(id)
        ?: throw NotFoundException("rate_plan_not_found", "Rate plan was not found.")

    private fun validateCompleteRules(plan: RatePlan) {
        val expected = ServiceLevel.entries.flatMap { service ->
            PricingZone.entries.map { zone -> service to zone }
        }.toSet()
        val present = plan.rules.map { it.serviceLevel to it.pricingZone }.toSet()
        val missing = expected - present
        if (missing.isNotEmpty()) {
            val details = missing.sortedWith(compareBy<Pair<ServiceLevel, PricingZone>> { it.first.name }.thenBy { it.second.name })
                .joinToString { "${it.first}/${it.second}" }
            throw ConflictException(
                "rate_plan_rules_incomplete",
                "A rate plan needs all Standard/Express pricing-zone rules before activation. Missing: $details.",
            )
        }
    }

    private fun validateWindow(effectiveFrom: Instant?, effectiveTo: Instant?) {
        if (effectiveFrom != null && effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw InvalidPricingRequestException(
                "invalid_effective_window",
                "Effective-to must be later than effective-from.",
            )
        }
    }

    private fun validateMonetaryLimits(
        codFreeThreshold: Long,
        maximumCod: Long,
        insuranceFreeThreshold: Long,
        maximumDeclaredValue: Long,
    ) {
        if (codFreeThreshold > maximumCod) {
            throw InvalidPricingRequestException(
                "invalid_cod_limits",
                "COD free threshold must not exceed the maximum COD amount.",
            )
        }
        if (insuranceFreeThreshold > maximumDeclaredValue) {
            throw InvalidPricingRequestException(
                "invalid_insurance_limits",
                "Insurance free threshold must not exceed the maximum declared value.",
            )
        }
    }

    private fun validateWeightLimits(maxParcelWeightKg: BigDecimal, maxShipmentWeightKg: BigDecimal) {
        if (maxShipmentWeightKg < maxParcelWeightKg) {
            throw InvalidPricingRequestException(
                "invalid_weight_limits",
                "Maximum shipment chargeable weight must be greater than or equal to maximum parcel weight.",
            )
        }
    }
}
