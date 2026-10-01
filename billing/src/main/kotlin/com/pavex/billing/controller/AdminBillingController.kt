package com.pavex.billing.controller

import com.pavex.billing.dto.CancelInvoiceRequest
import com.pavex.billing.dto.CashCollectionResponse
import com.pavex.billing.dto.CashRemittanceResponse
import com.pavex.billing.dto.CounterSettlementResponse
import com.pavex.billing.dto.GenerateStatementsRequest
import com.pavex.billing.dto.InvoiceListItemResponse
import com.pavex.billing.dto.InvoiceResponse
import com.pavex.billing.dto.PayoutAccountResponse
import com.pavex.billing.dto.RecordCashPaymentRequest
import com.pavex.billing.dto.RecordPayoutRequest
import com.pavex.billing.dto.RejectCashRequest
import com.pavex.billing.dto.SettlementStatementDetailResponse
import com.pavex.billing.dto.SettlementStatementResponse
import com.pavex.billing.entity.CashCollectionStatus
import com.pavex.billing.entity.CashRemittanceStatus
import com.pavex.billing.entity.CounterSettlementStatus
import com.pavex.billing.entity.InvoiceStatus
import com.pavex.billing.entity.PayoutStatus
import com.pavex.billing.payment.PaymentReconciliationService
import com.pavex.billing.payment.PaymentReconciliationSummary
import com.pavex.billing.service.CashService
import com.pavex.billing.service.CounterCashService
import com.pavex.billing.service.InvoiceService
import com.pavex.billing.service.SettlementService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Finance: invoices, payments, COD settlement and payouts. */
@RestController
@RequestMapping("/api/v1/admin/billing")
@PreAuthorize("hasAuthority('billing.manage')")
class AdminBillingController(
    private val invoices: InvoiceService,
    private val settlements: SettlementService,
    private val reconciliation: PaymentReconciliationService,
) {
    @GetMapping("/invoices")
    fun invoices(@RequestParam(required = false) merchantId: UUID?, @RequestParam(required = false) status: InvoiceStatus?): List<InvoiceListItemResponse> =
        invoices.list(merchantId, status)

    @GetMapping("/invoices/{invoiceId}")
    fun invoice(@PathVariable invoiceId: UUID): InvoiceResponse = invoices.get(invoiceId)

    @PostMapping("/invoices/{invoiceId}/payments/cash")
    fun recordCashPayment(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable invoiceId: UUID,
        @Valid @RequestBody request: RecordCashPaymentRequest,
    ): InvoiceResponse = invoices.recordCashPayment(invoiceId, request.amount, request.reference, jwt.userId())

    @PostMapping("/invoices/{invoiceId}/cancel")
    fun cancelInvoice(@AuthenticationPrincipal jwt: Jwt, @PathVariable invoiceId: UUID, @Valid @RequestBody request: CancelInvoiceRequest): InvoiceResponse =
        invoices.cancel(invoiceId, jwt.userId(), request.reason)

    /** Settles a payment made through the local fake gateway. */
    @PostMapping("/payments/{paymentId}/confirm")
    fun confirmQrPayment(@PathVariable paymentId: UUID): InvoiceResponse = invoices.confirmQrPayment(paymentId)

    @PostMapping("/payments/reconcile")
    fun reconcile(): PaymentReconciliationSummary = reconciliation.reconcile()

    @PostMapping("/statements/generate")
    fun generateStatements(@AuthenticationPrincipal jwt: Jwt, @RequestBody(required = false) request: GenerateStatementsRequest?): List<SettlementStatementResponse> =
        settlements.generate(jwt.userId(), request?.cutoffAt, request?.merchantId)

    @GetMapping("/statements")
    fun statements(@RequestParam(required = false) merchantId: UUID?, @RequestParam(required = false) payoutStatus: PayoutStatus?): List<SettlementStatementResponse> =
        settlements.list(merchantId, payoutStatus)

    @GetMapping("/statements/{statementId}")
    fun statement(@PathVariable statementId: UUID): SettlementStatementDetailResponse = settlements.get(statementId)

    @PostMapping("/statements/{statementId}/payout")
    fun recordPayout(@AuthenticationPrincipal jwt: Jwt, @PathVariable statementId: UUID, @Valid @RequestBody request: RecordPayoutRequest): SettlementStatementResponse =
        settlements.recordPayout(statementId, request.reference, jwt.userId())

    @GetMapping("/merchants/{merchantId}/payout-account")
    fun payoutAccount(@PathVariable merchantId: UUID): PayoutAccountResponse = settlements.payoutAccountOf(merchantId)
}

/** Finance review of cash that hubs received from couriers. */
@RestController
@RequestMapping("/api/v1/admin/billing/cash")
@PreAuthorize("hasAuthority('billing.cash.approve')")
class AdminCashController(
    private val cash: CashService,
    private val counterCash: CounterCashService,
) {
    /** COD paid back and return fees taken at hub counters, to reconcile with hub cash. */
    @GetMapping("/counter-settlements")
    fun counterSettlements(
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(required = false) status: CounterSettlementStatus?,
    ): List<CounterSettlementResponse> = counterCash.adminList(hubId, status)

    @GetMapping("/collections")
    fun collections(
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(required = false) collectorUserId: UUID?,
        @RequestParam(required = false) status: CashCollectionStatus?,
    ): List<CashCollectionResponse> = cash.adminCollections(hubId, collectorUserId, status)

    @GetMapping("/remittances")
    fun remittances(
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(required = false) courierUserId: UUID?,
        @RequestParam(required = false) status: CashRemittanceStatus?,
    ): List<CashRemittanceResponse> = cash.adminRemittances(hubId, courierUserId, status)

    @PostMapping("/remittances/{remittanceId}/approve")
    fun approveRemittance(@AuthenticationPrincipal jwt: Jwt, @PathVariable remittanceId: UUID): CashRemittanceResponse =
        cash.approveRemittance(remittanceId, jwt.userId())

    @PostMapping("/remittances/{remittanceId}/reject")
    fun rejectRemittance(@AuthenticationPrincipal jwt: Jwt, @PathVariable remittanceId: UUID, @Valid @RequestBody request: RejectCashRequest): CashRemittanceResponse =
        cash.rejectRemittance(remittanceId, jwt.userId(), request.reason)

    @PostMapping("/collections/{collectionId}/approve")
    fun approveCollection(@AuthenticationPrincipal jwt: Jwt, @PathVariable collectionId: UUID): CashCollectionResponse =
        cash.reviewCollection(collectionId, jwt.userId(), approve = true, reason = null)

    @PostMapping("/collections/{collectionId}/reject")
    fun rejectCollection(@AuthenticationPrincipal jwt: Jwt, @PathVariable collectionId: UUID, @Valid @RequestBody request: RejectCashRequest): CashCollectionResponse =
        cash.reviewCollection(collectionId, jwt.userId(), approve = false, reason = request.reason)
}
