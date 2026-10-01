package com.pavex.partners.controller

import com.pavex.partners.dto.AdminMerchantReadinessResponse
import com.pavex.partners.dto.AdminMerchantResponse
import com.pavex.partners.dto.ChangeMerchantStatusRequest
import com.pavex.partners.dto.MerchantListResponse
import com.pavex.partners.entity.MerchantStatus
import com.pavex.partners.exception.ApiException
import com.pavex.partners.service.AdminMerchantReadinessService
import com.pavex.partners.service.MerchantScopeResolver
import com.pavex.partners.service.MerchantService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/merchants")
class AdminMerchantController(
    private val merchants: MerchantService,
    private val readiness: AdminMerchantReadinessService,
    private val scopes: MerchantScopeResolver,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('partners.merchants.read')")
    fun list(
        authentication: Authentication,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) hubId: UUID?,
    ): MerchantListResponse =
        merchants.listAdmin(scopes.resolve(authentication), page, pageSize, parseStatus(status), search, hubId)

    @GetMapping("/{merchantId}")
    @PreAuthorize("hasAuthority('partners.merchants.read')")
    fun get(authentication: Authentication, @PathVariable merchantId: UUID): AdminMerchantResponse =
        merchants.getAdmin(merchantId, scopes.resolve(authentication))

    @GetMapping("/{merchantId}/readiness")
    @PreAuthorize("hasAuthority('partners.merchants.read')")
    fun readiness(authentication: Authentication, @PathVariable merchantId: UUID): AdminMerchantReadinessResponse =
        readiness.readiness(merchantId, scopes.resolve(authentication))

    @PatchMapping("/{merchantId}/status")
    @PreAuthorize("hasAuthority('partners.merchants.manage-status')")
    fun changeStatus(
        @AuthenticationPrincipal jwt: Jwt,
        authentication: Authentication,
        @PathVariable merchantId: UUID,
        @Valid @RequestBody request: ChangeMerchantStatusRequest,
    ): AdminMerchantResponse = merchants.changeStatus(merchantId, jwt.userId(), request, scopes.resolve(authentication))

    private fun parseStatus(value: String?): MerchantStatus? {
        if (value.isNullOrBlank()) return null
        return try {
            MerchantStatus.valueOf(value.trim().uppercase(Locale.ROOT))
        } catch (_: IllegalArgumentException) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_merchant_status", "Merchant status is invalid.")
        }
    }
}
