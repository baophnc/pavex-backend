package com.pavex.billing.controller

import com.pavex.billing.dto.CreateQrPaymentRequest
import com.pavex.billing.dto.InvoiceListItemResponse
import com.pavex.billing.dto.InvoiceResponse
import com.pavex.billing.dto.PayoutAccountResponse
import com.pavex.billing.dto.QrPaymentIntentResponse
import com.pavex.billing.dto.SettlementStatementDetailResponse
import com.pavex.billing.dto.SettlementStatementResponse
import com.pavex.billing.dto.UpdatePayoutAccountRequest
import com.pavex.billing.entity.InvoiceStatus
import com.pavex.billing.service.InvoiceService
import com.pavex.billing.service.SettlementService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** A merchant's own invoices, settlement statements and payout account. */
@RestController
@RequestMapping("/api/v1/billing")
class MerchantBillingController(
    private val invoices: InvoiceService,
    private val settlements: SettlementService,
) {
    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('billing.invoices.read-own')")
    fun invoices(@AuthenticationPrincipal jwt: Jwt, @RequestParam(required = false) status: InvoiceStatus?): List<InvoiceListItemResponse> =
        invoices.listMine(jwt.userId(), status)

    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAuthority('billing.invoices.read-own')")
    fun invoice(@AuthenticationPrincipal jwt: Jwt, @PathVariable invoiceId: UUID): InvoiceResponse =
        invoices.getMine(jwt.userId(), invoiceId)

    @PostMapping("/invoices/{invoiceId}/payments/qr")
    @PreAuthorize("hasAuthority('billing.invoices.pay-own')")
    fun createQrPayment(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable invoiceId: UUID,
        @Valid @RequestBody(required = false) request: CreateQrPaymentRequest?,
    ): QrPaymentIntentResponse = invoices.createQrPayment(jwt.userId(), invoiceId, request?.amount)

    @GetMapping("/statements")
    @PreAuthorize("hasAuthority('billing.invoices.read-own')")
    fun statements(@AuthenticationPrincipal jwt: Jwt): List<SettlementStatementResponse> = settlements.listMine(jwt.userId())

    @GetMapping("/statements/{statementId}")
    @PreAuthorize("hasAuthority('billing.invoices.read-own')")
    fun statement(@AuthenticationPrincipal jwt: Jwt, @PathVariable statementId: UUID): SettlementStatementDetailResponse =
        settlements.getMine(jwt.userId(), statementId)

    @GetMapping("/payout-account")
    @PreAuthorize("hasAuthority('billing.invoices.read-own')")
    fun payoutAccount(@AuthenticationPrincipal jwt: Jwt): PayoutAccountResponse = settlements.myPayoutAccount(jwt.userId())

    @PutMapping("/payout-account")
    @PreAuthorize("hasAuthority('partners.merchants.manage-own')")
    fun updatePayoutAccount(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: UpdatePayoutAccountRequest): PayoutAccountResponse =
        settlements.updateMyPayoutAccount(jwt.userId(), request)
}

internal fun Jwt.userId(): UUID = UUID.fromString(subject)
