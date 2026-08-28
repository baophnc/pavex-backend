package com.pavex.partners.controller

import com.pavex.partners.dto.PickupAddressRequest
import com.pavex.partners.dto.PickupAddressResponse
import com.pavex.partners.service.MerchantPickupAddressService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/merchants/me/pickup-addresses")
class MerchantPickupAddressController(
    private val pickupAddresses: MerchantPickupAddressService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: PickupAddressRequest,
    ): PickupAddressResponse = pickupAddresses.create(jwt.userId(), request)

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt): List<PickupAddressResponse> =
        pickupAddresses.list(jwt.userId())

    @GetMapping("/{addressId}")
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable addressId: UUID): PickupAddressResponse =
        pickupAddresses.get(jwt.userId(), addressId)

    @PutMapping("/{addressId}")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable addressId: UUID,
        @Valid @RequestBody request: PickupAddressRequest,
    ): PickupAddressResponse = pickupAddresses.update(jwt.userId(), addressId, request)

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable addressId: UUID) =
        pickupAddresses.delete(jwt.userId(), addressId)

    @PatchMapping("/{addressId}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setDefault(@AuthenticationPrincipal jwt: Jwt, @PathVariable addressId: UUID) =
        pickupAddresses.setDefault(jwt.userId(), addressId)
}
