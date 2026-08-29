package com.pavex.pricing.controller

import com.pavex.pricing.dto.CreatePricingQuoteRequest
import com.pavex.pricing.dto.PricingQuoteResponse
import com.pavex.pricing.service.QuoteService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/pricing/quotes")
class PricingQuoteController(
    private val quotes: QuoteService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun create(@RequestBody request: CreatePricingQuoteRequest): PricingQuoteResponse = quotes.create(request)
}
