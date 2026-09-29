package com.pavex.billing.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class InvoiceStatus { ISSUED, PARTIALLY_PAID, PAID, CANCELLED }

data class InvoiceLineDraft(val shipmentId: UUID, val trackingCode: String, val amount: Long)

/**
 * What a merchant owes Pavex. Payments reserve part of the balance while pending (a live QR code),
 * so two payments can never together exceed it.
 */
@Entity
@Table(name = "customer_invoices", schema = "billing")
class CustomerInvoice(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "invoice_number", nullable = false, length = 50)
    var invoiceNumber: String = "",
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 3)
    var currency: String = "VND",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: InvoiceStatus = InvoiceStatus.ISSUED,
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long = 0,
    @Column(name = "paid_amount", nullable = false)
    var paidAmount: Long = 0,
    @Column(name = "issued_at", nullable = false)
    var issuedAt: Instant = Instant.EPOCH,
    @Column(name = "due_at", nullable = false)
    var dueAt: Instant = Instant.EPOCH,
    @Column(name = "paid_at")
    var paidAt: Instant? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "cancelled_by_user_id")
    var cancelledByUserId: UUID? = null,
    @Column(name = "cancellation_reason", length = 500)
    var cancellationReason: String? = null,
    @Column(length = 500)
    var note: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    val lines: MutableList<InvoiceLine> = mutableListOf()

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    val payments: MutableList<InvoicePayment> = mutableListOf()

    fun balanceAmount(): Long = totalAmount - paidAmount

    fun applyCompletedPayment(amount: Long, paidAt: Instant) {
        requirePayable()
        require(amount > 0 && amount <= balanceAmount()) { "Payment amount must be positive and cannot exceed the invoice balance." }
        paidAmount += amount
        updatedAt = paidAt
        if (paidAmount == totalAmount) {
            status = InvoiceStatus.PAID
            this.paidAt = paidAt
        } else {
            status = InvoiceStatus.PARTIALLY_PAID
        }
    }

    fun cancel(actorUserId: UUID, reason: String, cancelledAt: Instant) {
        if (status == InvoiceStatus.CANCELLED) return
        check(paidAmount == 0L) { "An invoice with completed payments cannot be cancelled." }
        require(actorUserId != ZERO_UUID) { "Actor user id is required." }
        status = InvoiceStatus.CANCELLED
        this.cancelledAt = cancelledAt
        cancelledByUserId = actorUserId
        cancellationReason = BillingText.required(reason, 500, "reason")
        updatedAt = cancelledAt
        payments.filter { it.status == InvoicePaymentStatus.PENDING }.forEach { it.cancel(cancelledAt) }
    }

    fun addPayment(payment: InvoicePayment) {
        require(payment.invoice === this) { "Payment belongs to another invoice." }
        require(payments.none { it.id == payment.id }) { "Payment already belongs to this invoice." }
        payments += payment
        updatedAt = maxOf(updatedAt, payment.createdAt)
    }

    /** Expires QR payments whose code is no longer valid; returns whether anything changed. */
    fun expirePendingQrPayments(now: Instant): Boolean =
        payments.filter { it.method == InvoicePaymentMethod.QR }
            .map { it.expireIfNeeded(now) }
            .any { it }

    /** The balance not yet paid nor reserved by a pending payment. */
    fun availablePaymentAmount(now: Instant, excludedPaymentIds: Set<UUID> = emptySet()): Long {
        val reserved = payments
            .filter { it.reservesBalanceAt(now) && it.id !in excludedPaymentIds }
            .sumOf { it.amount }
        return maxOf(0, balanceAmount() - reserved)
    }

    fun isPayable(): Boolean = status != InvoiceStatus.CANCELLED && status != InvoiceStatus.PAID

    private fun requirePayable() {
        check(status != InvoiceStatus.CANCELLED) { "Cancelled invoices cannot be paid." }
        check(status != InvoiceStatus.PAID) { "Invoice is already paid." }
    }

    companion object {
        private val ZERO_UUID = UUID(0, 0)

        fun create(
            invoiceNumber: String,
            merchantId: UUID,
            lines: List<InvoiceLineDraft>,
            issuedAt: Instant,
            dueAt: Instant,
            note: String? = null,
        ): CustomerInvoice {
            require(lines.size in 1..MAX_LINES) { "An invoice must contain between 1 and $MAX_LINES shipment lines." }
            require(lines.map { it.shipmentId }.toSet().size == lines.size) { "Invoice shipment lines must be unique." }
            require(merchantId != ZERO_UUID) { "Merchant id is required." }
            require(!dueAt.isBefore(issuedAt)) { "Invoice due date cannot be before issue date." }

            val invoice = CustomerInvoice(
                invoiceNumber = BillingText.required(invoiceNumber, 50, "invoiceNumber"),
                merchantId = merchantId,
                issuedAt = issuedAt,
                dueAt = dueAt,
                note = BillingText.optional(note, 500, "note"),
                createdAt = issuedAt,
                updatedAt = issuedAt,
            )
            lines.forEachIndexed { index, draft ->
                invoice.lines += InvoiceLine.create(invoice, index + 1, draft, issuedAt)
            }
            invoice.totalAmount = invoice.lines.fold(0L) { total, line -> Math.addExact(total, line.amount) }
            require(invoice.totalAmount > 0) { "Invoice total must be positive." }
            return invoice
        }

        const val MAX_LINES = 1_000
    }
}
