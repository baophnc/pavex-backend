package com.pavex.pricing.service

import com.pavex.contracts.network.NetworkCoverageTier
import com.pavex.contracts.network.NetworkLocationResolutionResponse
import com.pavex.pricing.client.NetworkLocationClient
import com.pavex.pricing.dto.CreatePricingParcelRequest
import com.pavex.pricing.dto.CreatePricingQuoteRequest
import com.pavex.pricing.dto.PricingOptionResponse
import com.pavex.pricing.dto.PricingQuoteResponse
import com.pavex.pricing.entity.CoverageTier
import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.QuoteOption
import com.pavex.pricing.entity.QuoteParcel
import com.pavex.pricing.entity.QuoteRequest
import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.ServiceLevel
import com.pavex.pricing.exception.ConflictException
import com.pavex.pricing.exception.InvalidPricingRequestException
import com.pavex.pricing.exception.NotFoundException
import com.pavex.pricing.exception.PricingUnavailableException
import com.pavex.pricing.repository.QuoteRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@Service
class QuoteService(
    private val ratePlanService: RatePlanService,
    private val networkLocations: NetworkLocationClient,
    private val calculator: QuoteCalculator,
    private val quoteRequests: QuoteRequestRepository,
) {
    @Transactional
    fun create(request: CreatePricingQuoteRequest, now: Instant = Instant.now()): PricingQuoteResponse {
        validateLocations(request)
        val plan = activeRatePlan(now)
        val parcelInputs = resolveParcelInputs(request)
        val (parcelMetrics, weights) = calculator.validateAndMeasure(
            parcelInputs,
            plan,
            request.codAmount,
            request.declaredValue,
        )

        val origin = networkLocations.resolve(request.originProvinceCode, request.originWardCode)
        val destination = networkLocations.resolve(request.destinationProvinceCode, request.destinationWardCode)
        val zone = resolveZone(origin, destination)
        val originTier = origin.coverageTier.toLocal()
        val destinationTier = destination.coverageTier.toLocal()
        val prices = calculator.price(
            plan = plan,
            zone = zone,
            chargeableWeightKg = weights.chargeableWeightKg,
            originTier = originTier,
            destinationTier = destinationTier,
            codAmount = request.codAmount,
            declaredValue = request.declaredValue,
        )
        val expiresAt = now.plusSeconds(Math.multiplyExact(plan.quoteTtlMinutes.toLong(), 60L))
        val quote = QuoteRequest(
            ratePlanId = plan.id,
            rateCode = plan.code,
            rateRevision = plan.revision,
            rateVersion = rateVersion(plan),
            pricingZone = zone,
            originProvinceCode = request.originProvinceCode.trim(),
            originWardCode = request.originWardCode.trim(),
            originServiceAreaId = origin.serviceAreaId,
            originRegionId = origin.regionId,
            originPrimaryHubId = origin.primaryHubId,
            originCoverageTier = originTier,
            destinationProvinceCode = request.destinationProvinceCode.trim(),
            destinationWardCode = request.destinationWardCode.trim(),
            destinationServiceAreaId = destination.serviceAreaId,
            destinationRegionId = destination.regionId,
            destinationPrimaryHubId = destination.primaryHubId,
            destinationCoverageTier = destinationTier,
            actualWeightKg = weights.actualWeightKg,
            volumetricWeightKg = weights.volumetricWeightKg,
            chargeableWeightKg = weights.chargeableWeightKg,
            parcelCount = parcelMetrics.size,
            codAmount = request.codAmount,
            declaredValue = request.declaredValue,
            createdAt = now,
            expiresAt = expiresAt,
        )
        parcelMetrics.forEachIndexed { index, metrics ->
            quote.parcels += QuoteParcel(
                quoteRequest = quote,
                sequence = index + 1,
                actualWeightKg = metrics.actualWeightKg,
                volumetricWeightKg = metrics.volumetricWeightKg,
                chargeableWeightKg = metrics.chargeableWeightKg,
                lengthCm = metrics.lengthCm,
                widthCm = metrics.widthCm,
                heightCm = metrics.heightCm,
            )
        }
        prices.forEach { price ->
            quote.options += QuoteOption(
                quoteRequest = quote,
                serviceLevel = price.serviceLevel,
                baseFee = price.baseFee,
                additionalWeightFee = price.additionalWeightFee,
                areaSurcharge = price.areaSurcharge,
                codFee = price.codFee,
                insuranceFee = price.insuranceFee,
                total = price.total,
                estimatedMinDays = price.estimatedMinDays,
                estimatedMaxDays = price.estimatedMaxDays,
            )
        }
        val saved = quoteRequests.save(quote)
        return saved.toPublicResponse()
    }

    private fun activeRatePlan(now: Instant): RatePlan = try {
        ratePlanService.currentEntity(now)
    } catch (_: NotFoundException) {
        throw PricingUnavailableException(
            "active_rate_plan_not_found",
            "Pricing is temporarily unavailable because no active rate plan covers the current time.",
        )
    } catch (_: ConflictException) {
        throw PricingUnavailableException(
            "active_rate_plan_ambiguous",
            "Pricing is temporarily unavailable because multiple active rate plans overlap.",
        )
    }

    private fun validateLocations(request: CreatePricingQuoteRequest) {
        val values = listOf(
            request.originProvinceCode,
            request.originWardCode,
            request.destinationProvinceCode,
            request.destinationWardCode,
        )
        if (values.any { it.isBlank() || it.trim().length > 20 }) {
            throw InvalidPricingRequestException(
                "location_required",
                "Origin and destination province/ward codes are required.",
            )
        }
    }

    private fun resolveParcelInputs(request: CreatePricingQuoteRequest): List<CreatePricingParcelRequest> {
        val parcels = request.parcels
        if (!parcels.isNullOrEmpty()) return parcels
        val weight = request.weightKg
        val length = request.lengthCm
        val width = request.widthCm
        val height = request.heightCm
        if (weight == null || length == null || width == null || height == null) {
            throw InvalidPricingRequestException(
                "parcel_required",
                "Provide at least one parcel or the legacy top-level parcel dimensions.",
            )
        }
        return listOf(CreatePricingParcelRequest(weight, length, width, height))
    }

    private fun resolveZone(
        origin: NetworkLocationResolutionResponse,
        destination: NetworkLocationResolutionResponse,
    ): PricingZone = when {
        origin.serviceAreaId == destination.serviceAreaId -> PricingZone.SAME_SERVICE_AREA
        origin.regionId == destination.regionId -> PricingZone.SAME_REGION
        else -> PricingZone.CROSS_REGION
    }

    private fun rateVersion(plan: RatePlan): String = "${plan.code}-r${plan.revision}"
}

private fun NetworkCoverageTier.toLocal(): CoverageTier = CoverageTier.valueOf(name)

private fun QuoteRequest.toPublicResponse(): PricingQuoteResponse = PricingQuoteResponse(
    requestId = id,
    rateVersion = rateVersion,
    lane = pricingZone.toLegacyLane(),
    actualWeightKg = actualWeightKg,
    volumetricWeightKg = volumetricWeightKg,
    chargeableWeightKg = chargeableWeightKg,
    parcelCount = parcelCount,
    expiresAt = expiresAt,
    options = options
        .sortedBy { it.serviceLevel.ordinal }
        .map { it.toPublicResponse() },
)

private fun QuoteOption.toPublicResponse(): PricingOptionResponse = PricingOptionResponse(
    quoteId = id,
    serviceLevel = serviceLevel.toPublicName(),
    baseFee = baseFee,
    additionalWeightFee = additionalWeightFee,
    areaSurcharge = areaSurcharge,
    codFee = codFee,
    insuranceFee = insuranceFee,
    total = total,
    estimatedMinDays = estimatedMinDays,
    estimatedMaxDays = estimatedMaxDays,
)

private fun PricingZone.toLegacyLane(): String = when (this) {
    PricingZone.SAME_SERVICE_AREA -> "Local"
    PricingZone.SAME_REGION -> "Regional"
    PricingZone.CROSS_REGION -> "National"
}

private fun ServiceLevel.toPublicName(): String = when (this) {
    ServiceLevel.STANDARD -> "Standard"
    ServiceLevel.EXPRESS -> "Express"
}
