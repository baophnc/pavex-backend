package com.pavex.pricing.service

import com.pavex.pricing.dto.CreateRatePlanRequest
import com.pavex.pricing.dto.RatePlanResponse
import com.pavex.pricing.dto.UpdateRatePlanRequest
import com.pavex.pricing.dto.UpsertRateRuleRequest
import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.RatePlanStatus
import com.pavex.pricing.entity.ServiceLevel
import com.pavex.pricing.exception.ConflictException
import com.pavex.pricing.exception.InvalidPricingRequestException
import com.pavex.pricing.exception.NotFoundException
import com.pavex.pricing.repository.RatePlanRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RatePlanService(
    private val ratePlans: RatePlanRepository,
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
        return RatePlanResponse.from(ratePlans.save(plan))
    }

    @Transactional
    fun update(id: UUID, request: UpdateRatePlanRequest): RatePlanResponse {
        validateWindow(request.effectiveFrom, request.effectiveTo)
        validateWeightLimits(request.maxParcelWeightKg, request.maxShipmentChargeableWeightKg)
        validateMonetaryLimits(request.codFreeThreshold, request.maximumCod, request.insuranceFreeThreshold, request.maximumDeclaredValue)
        val plan = find(id)
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
        return RatePlanResponse.from(ratePlans.save(plan))
    }

    @Transactional
    fun upsertRule(
        id: UUID,
        serviceLevel: ServiceLevel,
        pricingZone: PricingZone,
        request: UpsertRateRuleRequest,
    ): RatePlanResponse {
        if (request.estimatedMaxDays < request.estimatedMinDays) {
            throw InvalidPricingRequestException(
                "invalid_eta_range",
                "Estimated maximum days must be greater than or equal to estimated minimum days.",
            )
        }
        val plan = find(id)
        plan.upsertRule(
            serviceLevel,
            pricingZone,
            request.baseFee,
            request.includedWeightKg,
            request.additionalWeightStepKg,
            request.additionalWeightStepFee,
            request.estimatedMinDays,
            request.estimatedMaxDays,
        )
        return RatePlanResponse.from(ratePlans.save(plan))
    }

    @Transactional
    fun deleteRule(id: UUID, serviceLevel: ServiceLevel, pricingZone: PricingZone): RatePlanResponse {
        val plan = find(id)
        if (!plan.removeRule(serviceLevel, pricingZone)) {
            throw NotFoundException("rate_rule_not_found", "Rate rule was not found.")
        }
        return RatePlanResponse.from(ratePlans.save(plan))
    }

    @Transactional
    fun deleteDraft(id: UUID) {
        val plan = find(id)
        plan.requireDraft()
        ratePlans.delete(plan)
    }

    fun find(id: UUID): RatePlan = ratePlans.findWithRulesById(id)
        ?: throw NotFoundException("rate_plan_not_found", "Rate plan was not found.")

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

    private fun validateWeightLimits(maxParcelWeightKg: java.math.BigDecimal, maxShipmentWeightKg: java.math.BigDecimal) {
        if (maxShipmentWeightKg < maxParcelWeightKg) {
            throw InvalidPricingRequestException(
                "invalid_weight_limits",
                "Maximum shipment chargeable weight must be greater than or equal to maximum parcel weight.",
            )
        }
    }
}
