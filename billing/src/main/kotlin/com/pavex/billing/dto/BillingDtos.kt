package com.pavex.billing.dto

import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.CashCollectionStatus
import com.pavex.billing.entity.CashRemittance
import com.pavex.billing.entity.CashRemittanceStatus
import com.pavex.billing.entity.CashSource
import com.pavex.billing.entity.CodPayee
import com.pavex.billing.entity.CounterSettlement
import com.pavex.billing.entity.CounterSettlementKind
import com.pavex.billing.entity.CounterSettlementStatus
import com.pavex.billing.entity.CustomerInvoice
import com.pavex.billing.entity.InvoicePayment
import com.pavex.billing.entity.InvoicePaymentMethod
import com.pavex.billing.entity.InvoicePaymentStatus
import com.pavex.billing.entity.InvoiceStatus
import com.pavex.billing.entity.MerchantFeeCharge
import com.pavex.billing.entity.MerchantPayoutAccount
import com.pavex.billing.entity.PayoutStatus
import com.pavex.billing.entity.SettlementStatement
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// Invoices keep the core-api contract the client portal already reads.

data class InvoiceListItemResponse(
    val id: UUID,
    val invoiceNumber: String,
    val merchantId: UUID,
    val status: InvoiceStatus,
    val totalAmount: Long,
    val paidAmount: Long,
    val balanceAmount: Long,
    val issuedAt: Instant,
    val dueAt: Instant,
) {
    companion object {
        fun from(invoice: CustomerInvoice) = InvoiceListItemResponse(
            invoice.id, invoice.invoiceNumber, invoice.merchantId, invoice.status,
            invoice.totalAmount, invoice.paidAmount, invoice.balanceAmount(), invoice.issuedAt, invoice.dueAt,
        )
    }
}

data class InvoiceLineResponse(
    val id: UUID,
    val sequence: Int,
    val shipmentId: UUID,
    val trackingCode: String,
    val amount: Long,
)

data class InvoicePaymentResponse(
    val id: UUID,
    val method: InvoicePaymentMethod,
    val status: InvoicePaymentStatus,
    val amount: Long,
    val reference: String,
    val provider: String?,
    val providerPaymentId: String?,
    val providerReference: String?,
    val checkoutUrl: String?,
    val qrPayload: String?,
    val qrExpiresAt: Instant?,
    val collectedByUserId: UUID?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(payment: InvoicePayment) = InvoicePaymentResponse(
            payment.id, payment.method, payment.status, payment.amount, payment.reference, payment.provider,
            payment.providerPaymentId, payment.providerReference, payment.checkoutUrl, payment.qrPayload,
            payment.qrExpiresAt, payment.collectedByUserId, payment.completedAt, payment.cancelledAt, payment.createdAt,
        )
    }
}

data class InvoiceResponse(
    val id: UUID,
    val invoiceNumber: String,
    val merchantId: UUID,
    val currency: String,
    val status: InvoiceStatus,
    val totalAmount: Long,
    val paidAmount: Long,
    val balanceAmount: Long,
    val issuedAt: Instant,
    val dueAt: Instant,
    val paidAt: Instant?,
    val cancelledAt: Instant?,
    val cancellationReason: String?,
    val note: String?,
    val lines: List<InvoiceLineResponse>,
    val payments: List<InvoicePaymentResponse>,
) {
    companion object {
        fun from(invoice: CustomerInvoice) = InvoiceResponse(
            invoice.id, invoice.invoiceNumber, invoice.merchantId, invoice.currency, invoice.status,
            invoice.totalAmount, invoice.paidAmount, invoice.balanceAmount(), invoice.issuedAt, invoice.dueAt,
            invoice.paidAt, invoice.cancelledAt, invoice.cancellationReason, invoice.note,
            invoice.lines.sortedBy { it.sequence }.map { InvoiceLineResponse(it.id, it.sequence, it.shipmentId, it.trackingCode, it.amount) },
            invoice.payments.sortedBy { it.createdAt }.map(InvoicePaymentResponse::from),
        )
    }
}

data class QrPaymentIntentResponse(
    val paymentId: UUID,
    val invoiceId: UUID,
    val amount: Long,
    val currency: String,
    val reference: String,
    val provider: String,
    val providerPaymentId: String,
    val providerReference: String?,
    val checkoutUrl: String?,
    val qrPayload: String,
    val expiresAt: Instant,
)

data class CreateQrPaymentRequest(
    @field:Positive
    val amount: Long? = null,
)

data class RecordCashPaymentRequest(
    @field:Positive
    val amount: Long,
    @field:Size(max = 100)
    val reference: String? = null,
)

data class CancelInvoiceRequest(
    @field:NotBlank @field:Size(max = 500)
    val reason: String,
)

// Courier cash custody.

data class CashCollectionResponse(
    val id: UUID,
    val shipmentId: UUID,
    val trackingCode: String,
    /** Null for a walk-in (counter) shipment. */
    val merchantId: UUID?,
    val status: CashCollectionStatus,
    val amount: Long,
    val codAmount: Long,
    val feeAmount: Long,
    val currency: String,
    val reference: String,
    val collectedByUserId: UUID,
    val originHubId: UUID,
    val currentCustodianUserId: UUID?,
    val currentHubId: UUID?,
    val currentRemittanceId: UUID?,
    val collectedAt: Instant,
    val submittedAt: Instant?,
    val approvedAt: Instant?,
    val approvedByUserId: UUID?,
    val lastRejectedAt: Instant?,
    val lastRejectionReason: String?,
    val statementId: UUID?,
    val updatedAt: Instant,
    /** DELIVERY: taken by a courier; COUNTER: a walk-in sender paid it at the hub counter. */
    val source: CashSource = CashSource.DELIVERY,
    val codPayee: CodPayee = CodPayee.MERCHANT,
) {
    companion object {
        fun from(c: CashCollection) = CashCollectionResponse(
            c.id, c.shipmentId, c.trackingCode, c.merchantId, c.status, c.amount, c.codAmount, c.feeAmount,
            c.currency, c.reference, c.collectedByUserId, c.originHubId, c.currentCustodianUserId, c.currentHubId,
            c.currentRemittanceId, c.collectedAt, c.submittedAt, c.approvedAt, c.approvedByUserId, c.lastRejectedAt,
            c.lastRejectionReason, c.statementId, c.updatedAt, c.source, c.codPayee,
        )
    }
}

data class CashRemittanceItemResponse(val collectionId: UUID, val sequence: Int, val amount: Long)

data class CashRemittanceResponse(
    val id: UUID,
    val remittanceNumber: String,
    val courierUserId: UUID,
    val hubId: UUID,
    val status: CashRemittanceStatus,
    val expectedAmount: Long,
    val declaredAmount: Long,
    val submittedAt: Instant,
    val approvedAt: Instant?,
    val approvedByUserId: UUID?,
    val rejectedAt: Instant?,
    val rejectedByUserId: UUID?,
    val rejectionReason: String?,
    val items: List<CashRemittanceItemResponse>,
) {
    companion object {
        fun from(r: CashRemittance) = CashRemittanceResponse(
            r.id, r.remittanceNumber, r.courierUserId, r.hubId, r.status, r.expectedAmount, r.declaredAmount,
            r.submittedAt, r.approvedAt, r.approvedByUserId, r.rejectedAt, r.rejectedByUserId, r.rejectionReason,
            r.items.sortedBy { it.sequence }.map { CashRemittanceItemResponse(it.collectionId, it.sequence, it.amount) },
        )
    }
}

data class SubmitCashRemittanceRequest(
    val hubId: UUID,
    @field:NotEmpty @field:Size(max = 200)
    val collectionIds: List<UUID>,
    @field:Positive
    val declaredAmount: Long,
)

data class RejectCashRequest(
    @field:NotBlank @field:Size(max = 500)
    val reason: String,
)

// COD settlement.

data class SettlementStatementResponse(
    val id: UUID,
    val statementNumber: String,
    val merchantId: UUID,
    val cutoffAt: Instant,
    val codTotal: Long,
    val feeTotal: Long,
    val offsetAmount: Long,
    val payoutAmount: Long,
    val payoutStatus: PayoutStatus,
    val payoutReference: String?,
    val payoutBankName: String?,
    val payoutAccountNumber: String?,
    val payoutAccountName: String?,
    val paidAt: Instant?,
    val invoice: InvoiceListItemResponse?,
    val createdAt: Instant,
) {
    companion object {
        fun from(s: SettlementStatement, invoice: CustomerInvoice?) = SettlementStatementResponse(
            s.id, s.statementNumber, s.merchantId, s.cutoffAt, s.codTotal, s.feeTotal, s.offsetAmount, s.payoutAmount,
            s.payoutStatus, s.payoutReference, s.payoutBankName, s.payoutAccountNumber, s.payoutAccountName, s.paidAt,
            invoice?.let(InvoiceListItemResponse::from), s.createdAt,
        )
    }
}

data class SettlementCodLineResponse(
    val shipmentId: UUID,
    val trackingCode: String,
    val codAmount: Long,
    val collectedAt: Instant,
    val approvedAt: Instant?,
)

data class SettlementFeeLineResponse(
    val shipmentId: UUID,
    val trackingCode: String,
    val amount: Long,
    val shippingFeePayer: String,
    val finalStatus: String,
    val closedAt: Instant,
) {
    companion object {
        fun from(f: MerchantFeeCharge) =
            SettlementFeeLineResponse(f.shipmentId, f.trackingCode, f.amount, f.shippingFeePayer, f.finalStatus, f.closedAt)
    }
}

data class SettlementStatementDetailResponse(
    val statement: SettlementStatementResponse,
    val codLines: List<SettlementCodLineResponse>,
    val feeLines: List<SettlementFeeLineResponse>,
)

data class GenerateStatementsRequest(
    /** Settle everything up to this time; defaults to now. */
    val cutoffAt: Instant? = null,
    val merchantId: UUID? = null,
)

data class RecordPayoutRequest(
    @field:NotBlank @field:Size(max = 100)
    val reference: String,
)

data class PayoutAccountResponse(
    val merchantId: UUID,
    val bankName: String,
    val accountNumber: String,
    val accountName: String,
    val updatedAt: Instant,
) {
    companion object {
        fun from(a: MerchantPayoutAccount) = PayoutAccountResponse(a.merchantId, a.bankName, a.accountNumber, a.accountName, a.updatedAt)
    }
}

data class UpdatePayoutAccountRequest(
    @field:NotBlank @field:Size(max = 100)
    val bankName: String,
    @field:NotBlank @field:Size(max = 40)
    val accountNumber: String,
    @field:NotBlank @field:Size(max = 200)
    val accountName: String,
)

/** Cash a counter owes a walk-in sender (their COD) or is owed by them (a return's fee). */
data class CounterSettlementResponse(
    val id: UUID,
    val shipmentId: UUID,
    val trackingCode: String,
    val hubId: UUID,
    val kind: CounterSettlementKind,
    val amount: Long,
    val senderName: String,
    val senderPhone: String,
    val status: CounterSettlementStatus,
    val completedByUserId: UUID?,
    val completedAt: Instant?,
    val note: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(s: CounterSettlement) = CounterSettlementResponse(
            s.id, s.shipmentId, s.trackingCode, s.hubId, s.kind, s.amount, s.senderName, s.senderPhone,
            s.status, s.completedByUserId, s.completedAt, s.note, s.createdAt,
        )
    }
}

/** The phone the sender gives at the counter must be the one the shipment was booked with. */
data class CompleteCounterSettlementRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val phone: String,
    @field:Size(max = 500)
    val note: String? = null,
)

/**
 * Cash finance approved in a period. The fee part is Pavex's revenue; the COD part is owed on to
 * merchants (through settlement) or to walk-in senders (paid back at the counter).
 */
data class ApprovedCashResponse(
    val from: java.time.LocalDate,
    val to: java.time.LocalDate,
    val count: Long,
    val totalAmount: Long,
    /** Shipping fees collected in cash: from recipients on delivery and from senders at the counter. */
    val feeRevenue: Long,
    val counterFeeRevenue: Long,
    val deliveryFeeRevenue: Long,
    val codOwedToMerchants: Long,
    val codOwedToSenders: Long,
    /**
     * Fees merchants pay themselves for shipments closed in the period: no cash moves, they are
     * netted in settlement. Null when the view is narrowed to one hub (merchants have no hub).
     */
    val merchantFeeRevenue: Long?,
    /** The newest approved rows, at most [ITEM_LIMIT]; the totals above cover the whole period. */
    val items: List<CashCollectionResponse>,
) {
    companion object {
        const val ITEM_LIMIT = 300
    }
}
