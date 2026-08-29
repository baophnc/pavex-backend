package com.pavex.partners.controller

import com.pavex.contracts.partners.MerchantOrderCapabilitySnapshot
import com.pavex.partners.service.MerchantService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.security.access.prepost.PreAuthorize
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
    @PreAuthorize("hasAuthority('SCOPE_partners.orders.evaluate')")
    fun orderCapability(@PathVariable userId: UUID): MerchantOrderCapabilitySnapshot {
        val capability = merchants.orderCapability(userId)
        return MerchantOrderCapabilitySnapshot(
            merchantId = capability.merchantId,
            merchantCode = capability.merchantCode,
            userId = capability.userId,
            status = capability.status?.name,
            canCreateOrders = capability.canCreateOrder,
            defaultPickupAddressId = capability.defaultPickupAddressId,
            restrictions = capability.restrictions,
        )
    }
}
