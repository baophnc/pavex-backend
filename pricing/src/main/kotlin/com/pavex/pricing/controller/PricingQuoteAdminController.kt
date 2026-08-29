package com.pavex.pricing.controller

import com.pavex.contracts.pricing.PricingQuoteSnapshot
import com.pavex.pricing.service.PricingQuoteContractService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/pricing/quotes")
class PricingQuoteAdminController(
    private val quoteContracts: PricingQuoteContractService,
) {
    @GetMapping("/{quoteId}")
    @PreAuthorize("hasAnyAuthority('pricing.read','pricing.manage')")
    fun get(@PathVariable quoteId: UUID): PricingQuoteSnapshot = quoteContracts.getSnapshot(quoteId)
}
