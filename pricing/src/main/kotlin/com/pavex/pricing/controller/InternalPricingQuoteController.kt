package com.pavex.pricing.controller

import com.pavex.contracts.pricing.PricingQuoteSnapshot
import com.pavex.pricing.service.PricingQuoteContractService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/pricing/quotes")
class InternalPricingQuoteController(
    private val quoteContracts: PricingQuoteContractService,
) {
    @GetMapping("/{quoteId}")
    fun getForShipment(@PathVariable quoteId: UUID): PricingQuoteSnapshot = quoteContracts.getForShipment(quoteId)
}
