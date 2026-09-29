package com.pavex.partners.controller

import com.pavex.contracts.partners.MerchantAccountSnapshot
import com.pavex.contracts.partners.MerchantPickupAddressSnapshot
import com.pavex.contracts.partners.MerchantOrderCapabilitySnapshot
import com.pavex.partners.exception.ApiException
import com.pavex.partners.service.MerchantPickupAddressService
import com.pavex.partners.service.MerchantService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
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
    private val pickupAddresses: MerchantPickupAddressService,
) {
    @GetMapping("/{merchantId}/pickup-addresses/{pickupAddressId}")
    @PreAuthorize("hasAuthority('SCOPE_partners.pickup-addresses.read')")
    fun pickupAddress(@PathVariable merchantId: UUID, @PathVariable pickupAddressId: UUID): MerchantPickupAddressSnapshot {
        val address = pickupAddresses.findForMerchant(merchantId, pickupAddressId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "pickup_address_not_found", "Pickup address was not found.")
        return MerchantPickupAddressSnapshot(
            id = address.id,
            merchantId = address.merchantId,
            name = address.name,
            contactName = address.contactName,
            contactPhone = address.contactPhone,
            addressLine = address.addressLine,
            provinceCode = address.provinceCode,
            wardCode = address.wardCode,
        )
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasAuthority('SCOPE_partners.merchants.read')")
    fun byUser(@PathVariable userId: UUID): MerchantAccountSnapshot {
        val merchant = merchants.findAccountByUser(userId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "merchant_not_found", "Merchant profile was not found.")
        return MerchantAccountSnapshot(
            merchantId = merchant.id,
            merchantCode = merchant.merchantCode,
            businessName = merchant.businessName,
            status = merchant.status.name,
        )
    }

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
