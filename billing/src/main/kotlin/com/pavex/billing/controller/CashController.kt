package com.pavex.billing.controller

import com.pavex.billing.dto.CashCollectionResponse
import com.pavex.billing.dto.CashRemittanceResponse
import com.pavex.billing.dto.SubmitCashRemittanceRequest
import com.pavex.billing.entity.CashCollectionStatus
import com.pavex.billing.entity.CashRemittanceStatus
import com.pavex.billing.service.CashService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Couriers see and hand over the cash they hold; hubs see the cash in their custody. */
@RestController
@RequestMapping("/api/v1/billing/cash")
class CashController(
    private val cash: CashService,
) {
    @GetMapping("/collections/mine")
    @PreAuthorize("hasAuthority('billing.cash.collect')")
    fun myCollections(@AuthenticationPrincipal jwt: Jwt, @RequestParam(required = false) status: CashCollectionStatus?): List<CashCollectionResponse> =
        cash.mine(jwt.userId(), status)

    @PostMapping("/remittances")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('billing.cash.submit-own')")
    fun submitRemittance(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: SubmitCashRemittanceRequest): CashRemittanceResponse =
        cash.submitRemittance(jwt.userId(), request.hubId, request.collectionIds, request.declaredAmount)

    @GetMapping("/remittances/mine")
    @PreAuthorize("hasAuthority('billing.cash.submit-own')")
    fun myRemittances(@AuthenticationPrincipal jwt: Jwt, @RequestParam(required = false) status: CashRemittanceStatus?): List<CashRemittanceResponse> =
        cash.myRemittances(jwt.userId(), status)

    @GetMapping("/hubs/{hubId}/collections")
    @PreAuthorize("hasAuthority('billing.cash.read-hub')")
    fun hubCollections(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable hubId: UUID,
        @RequestParam(required = false) status: CashCollectionStatus?,
    ): List<CashCollectionResponse> = cash.hubCollections(jwt.userId(), hubId, status)

    @PostMapping("/collections/{collectionId}/resubmit")
    @PreAuthorize("hasAuthority('billing.cash.read-hub')")
    fun resubmit(@AuthenticationPrincipal jwt: Jwt, @PathVariable collectionId: UUID): CashCollectionResponse =
        cash.resubmitFromHub(jwt.userId(), collectionId)
}
