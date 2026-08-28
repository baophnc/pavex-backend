package com.pavex.partners.controller

import com.pavex.partners.dto.MerchantProfileRequest
import com.pavex.partners.dto.MerchantProfileResponse
import com.pavex.partners.service.MerchantService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/merchants/me/profile")
class MerchantController(
    private val merchants: MerchantService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: MerchantProfileRequest,
    ): MerchantProfileResponse = merchants.create(jwt.userId(), request)

    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt): MerchantProfileResponse = merchants.getMine(jwt.userId())

    @PutMapping
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: MerchantProfileRequest,
    ): MerchantProfileResponse = merchants.updateMine(jwt.userId(), request)

    @PostMapping("/submit-verification")
    fun submitVerification(@AuthenticationPrincipal jwt: Jwt): MerchantProfileResponse =
        merchants.resubmit(jwt.userId())
}
