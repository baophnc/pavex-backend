package com.pavex.partners.controller

import com.pavex.partners.dto.OrderCapabilityResponse
import com.pavex.partners.service.MerchantService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Hidden
@RestController
@RequestMapping("/internal/v1/merchants")
class InternalMerchantController(
    private val merchants: MerchantService,
) {
    @GetMapping("/by-user/{userId}/order-capability")
    fun orderCapability(@PathVariable userId: UUID): OrderCapabilityResponse =
        merchants.orderCapability(userId)
}
