package com.pavex.pricing.service

import com.pavex.contracts.pricing.PricingQuoteCoverageTier
import com.pavex.contracts.pricing.PricingQuoteFeeSnapshot
import com.pavex.contracts.pricing.PricingQuoteLocationSnapshot
import com.pavex.contracts.pricing.PricingQuoteParcelSnapshot
import com.pavex.contracts.pricing.PricingQuoteServiceLevel
import com.pavex.contracts.pricing.PricingQuoteSnapshot
import com.pavex.contracts.pricing.PricingQuoteZone
import com.pavex.pricing.entity.QuoteOption
import com.pavex.pricing.exception.NotFoundException
import com.pavex.pricing.exception.QuoteExpiredException
import com.pavex.pricing.repository.QuoteOptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PricingQuoteContractService(
    private val quoteOptions: QuoteOptionRepository,
) {
    @Transactional(readOnly = true)
    fun getForShipment(quoteId: UUID, now: Instant = Instant.now()): PricingQuoteSnapshot {
        val option = quoteOptions.findDetailedById(quoteId)
            ?: throw NotFoundException("pricing_quote_not_found", "Pricing quote was not found.")
        if (!option.quoteRequest.expiresAt.isAfter(now)) {
            throw QuoteExpiredException("pricing_quote_expired", "Pricing quote has expired. Request a new quote before creating the shipment.")
        }
        return option.toContract()
    }

    @Transactional(readOnly = true)
    fun getSnapshot(quoteId: UUID): PricingQuoteSnapshot = quoteOptions.findDetailedById(quoteId)?.toContract()
        ?: throw NotFoundException("pricing_quote_not_found", "Pricing quote was not found.")
}

private fun QuoteOption.toContract(): PricingQuoteSnapshot {
    val request = quoteRequest
    return PricingQuoteSnapshot(
        quoteId = id,
        requestId = request.id,
        ratePlanId = request.ratePlanId,
        rateCode = request.rateCode,
        rateRevision = request.rateRevision,
        rateVersion = request.rateVersion,
        currency = "VND",
        serviceLevel = PricingQuoteServiceLevel.valueOf(serviceLevel.name),
        pricingZone = PricingQuoteZone.valueOf(request.pricingZone.name),
        origin = PricingQuoteLocationSnapshot(
            provinceCode = request.originProvinceCode,
            wardCode = request.originWardCode,
            serviceAreaId = request.originServiceAreaId,
            regionId = request.originRegionId,
            primaryHubId = request.originPrimaryHubId,
            coverageTier = PricingQuoteCoverageTier.valueOf(request.originCoverageTier.name),
        ),
        destination = PricingQuoteLocationSnapshot(
            provinceCode = request.destinationProvinceCode,
            wardCode = request.destinationWardCode,
            serviceAreaId = request.destinationServiceAreaId,
            regionId = request.destinationRegionId,
            primaryHubId = request.destinationPrimaryHubId,
            coverageTier = PricingQuoteCoverageTier.valueOf(request.destinationCoverageTier.name),
        ),
        actualWeightKg = request.actualWeightKg,
        volumetricWeightKg = request.volumetricWeightKg,
        chargeableWeightKg = request.chargeableWeightKg,
        parcelCount = request.parcelCount,
        parcels = request.parcels.sortedBy { it.sequence }.map { parcel ->
            PricingQuoteParcelSnapshot(
                sequence = parcel.sequence,
                actualWeightKg = parcel.actualWeightKg,
                volumetricWeightKg = parcel.volumetricWeightKg,
                chargeableWeightKg = parcel.chargeableWeightKg,
                lengthCm = parcel.lengthCm,
                widthCm = parcel.widthCm,
                heightCm = parcel.heightCm,
            )
        },
        codAmount = request.codAmount,
        declaredValue = request.declaredValue,
        fees = PricingQuoteFeeSnapshot(
            baseFee = baseFee,
            additionalWeightFee = additionalWeightFee,
            areaSurcharge = areaSurcharge,
            codFee = codFee,
            insuranceFee = insuranceFee,
            total = total,
        ),
        estimatedMinDays = estimatedMinDays,
        estimatedMaxDays = estimatedMaxDays,
        createdAt = request.createdAt,
        expiresAt = request.expiresAt,
    )
}
