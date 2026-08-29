package com.pavex.pricing.service

import com.pavex.pricing.entity.CoverageTier
import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.QuoteOption
import com.pavex.pricing.entity.QuoteParcel
import com.pavex.pricing.entity.QuoteRequest
import com.pavex.pricing.entity.ServiceLevel
import com.pavex.pricing.exception.QuoteExpiredException
import com.pavex.pricing.repository.QuoteOptionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PricingQuoteContractServiceTest {
    private val repository = mock(QuoteOptionRepository::class.java)
    private val service = PricingQuoteContractService(repository)

    @Test
    fun `returns immutable shipment snapshot for a valid quote option`() {
        val now = Instant.parse("2026-08-29T04:00:00Z")
        val option = quote(now.plusSeconds(600))
        `when`(repository.findDetailedById(option.id)).thenReturn(option)

        val snapshot = service.getForShipment(option.id, now)

        assertEquals(option.id, snapshot.quoteId)
        assertEquals("DEMO-r1", snapshot.rateVersion)
        assertEquals(1, snapshot.parcels.size)
        assertEquals(30_000, snapshot.fees.total)
        assertEquals("01", snapshot.origin.provinceCode)
        assertEquals("79", snapshot.destination.provinceCode)
    }

    @Test
    fun `rejects expired quote for shipment`() {
        val now = Instant.parse("2026-08-29T04:00:00Z")
        val option = quote(now)
        `when`(repository.findDetailedById(option.id)).thenReturn(option)

        assertThrows(QuoteExpiredException::class.java) {
            service.getForShipment(option.id, now)
        }
    }

    private fun quote(expiresAt: Instant): QuoteOption {
        val request = QuoteRequest(
            ratePlanId = UUID.randomUUID(),
            rateCode = "DEMO",
            rateRevision = 1,
            rateVersion = "DEMO-r1",
            pricingZone = PricingZone.CROSS_REGION,
            originProvinceCode = "01",
            originWardCode = "00070",
            originServiceAreaId = UUID.randomUUID(),
            originRegionId = UUID.randomUUID(),
            originPrimaryHubId = UUID.randomUUID(),
            originCoverageTier = CoverageTier.STANDARD,
            destinationProvinceCode = "79",
            destinationWardCode = "26740",
            destinationServiceAreaId = UUID.randomUUID(),
            destinationRegionId = UUID.randomUUID(),
            destinationPrimaryHubId = UUID.randomUUID(),
            destinationCoverageTier = CoverageTier.STANDARD,
            actualWeightKg = BigDecimal("1.00"),
            volumetricWeightKg = BigDecimal("1.20"),
            chargeableWeightKg = BigDecimal("1.50"),
            parcelCount = 1,
            codAmount = 0,
            declaredValue = 0,
            createdAt = expiresAt.minusSeconds(600),
            expiresAt = expiresAt,
        )
        request.parcels += QuoteParcel(
            quoteRequest = request,
            sequence = 1,
            actualWeightKg = BigDecimal("1.00"),
            volumetricWeightKg = BigDecimal("1.20"),
            chargeableWeightKg = BigDecimal("1.50"),
            lengthCm = BigDecimal("20"),
            widthCm = BigDecimal("20"),
            heightCm = BigDecimal("20"),
        )
        return QuoteOption(
            quoteRequest = request,
            serviceLevel = ServiceLevel.STANDARD,
            baseFee = 30_000,
            total = 30_000,
            estimatedMinDays = 2,
            estimatedMaxDays = 3,
        )
    }
}
