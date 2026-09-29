package com.pavex.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/** CASH is recorded by finance, QR goes through a payment gateway, COD_OFFSET settles fees from collected COD. */
enum class InvoicePaymentMethod { CASH, QR, COD_OFFSET }
enum class InvoicePaymentStatus { PENDING, COMPLETED, CANCELLED, EXPIRED }

@Entity
@Table(name = "invoice_payments", schema = "billing")
class InvoicePayment(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    var invoice: CustomerInvoice = CustomerInvoice(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var method: InvoicePaymentMethod = InvoicePaymentMethod.CASH,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: InvoicePaymentStatus = InvoicePaymentStatus.PENDING,
    @Column(nullable = false)
    var amount: Long = 0,
    @Column(nullable = false, length = 100)
    var reference: String = "",
    @Column(length = 30)
    var provider: String? = null,
    @Column(name = "provider_payment_id", length = 200)
    var providerPaymentId: String? = null,
    @Column(name = "provider_reference", length = 200)
    var providerReference: String? = null,
    @Column(name = "checkout_url", length = 2048)
    var checkoutUrl: String? = null,
    @Column(name = "qr_payload", length = 2000)
    var qrPayload: String? = null,
    @Column(name = "qr_expires_at")
    var qrExpiresAt: Instant? = null,
    @Column(name = "collected_by_user_id")
    var collectedByUserId: UUID? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    fun isGatewayInitialized(): Boolean =
        method == InvoicePaymentMethod.QR &&
            !provider.isNullOrBlank() &&
            !providerPaymentId.isNullOrBlank() &&
            !qrPayload.isNullOrBlank()

    fun attachGatewayIntent(
        providerPaymentId: String,
        providerReference: String?,
        qrPayload: String,
        checkoutUrl: String?,
        expiresAt: Instant,
        updatedAt: Instant,
    ) {
        check(method == InvoicePaymentMethod.QR) { "Only QR payments can attach a gateway intent." }
        check(status == InvoicePaymentStatus.PENDING) { "Only pending payments can attach a gateway intent." }
        check(!provider.isNullOrBlank()) { "Payment provider is missing." }
        require(expiresAt.isAfter(updatedAt)) { "Gateway payment expiry must be in the future." }
        this.providerPaymentId = BillingText.required(providerPaymentId, 200, "providerPaymentId")
        this.providerReference = BillingText.optional(providerReference, 200, "providerReference")
        this.qrPayload = BillingText.required(qrPayload, 2000, "qrPayload")
        this.checkoutUrl = BillingText.optional(checkoutUrl, 2048, "checkoutUrl")
        qrExpiresAt = expiresAt
        this.updatedAt = updatedAt
    }

    fun expireIfNeeded(now: Instant): Boolean {
        val expiresAt = qrExpiresAt ?: return false
        if (status != InvoicePaymentStatus.PENDING || expiresAt.isAfter(now)) return false
        status = InvoicePaymentStatus.EXPIRED
        updatedAt = now
        return true
    }

    fun reservesBalanceAt(now: Instant): Boolean =
        status == InvoicePaymentStatus.PENDING &&
            (method != InvoicePaymentMethod.QR || qrExpiresAt?.isAfter(now) == true)

    /** Settles a fake-gateway QR payment after an explicit confirmation. Returns false if already completed. */
    fun complete(completedAt: Instant): Boolean {
        if (status == InvoicePaymentStatus.COMPLETED) return false
        check(status == InvoicePaymentStatus.PENDING) { "Only pending payments can be completed." }
        check(method != InvoicePaymentMethod.QR || isGatewayInitialized()) { "QR payment gateway intent has not been initialized." }
        check(qrExpiresAt?.isAfter(completedAt) ?: true) { "QR payment has expired." }
        markCompleted(completedAt)
        return true
    }

    /** The provider confirmed the money arrived; an expired code that was still paid counts. */
    fun completeFromProvider(completedAt: Instant): Boolean {
        if (status == InvoicePaymentStatus.COMPLETED) return false
        check(method == InvoicePaymentMethod.QR) { "Only QR payments can be settled by a payment provider." }
        check(isGatewayInitialized()) { "QR payment gateway intent has not been initialized." }
        check(status == InvoicePaymentStatus.PENDING || status == InvoicePaymentStatus.EXPIRED) {
            "Only pending or expired QR payments can be settled by a payment provider."
        }
        markCompleted(completedAt)
        return true
    }

    fun cancel(cancelledAt: Instant) {
        if (status != InvoicePaymentStatus.PENDING) return
        markCancelled(cancelledAt)
    }

    fun cancelFromProvider(cancelledAt: Instant): Boolean {
        if (status == InvoicePaymentStatus.CANCELLED) return false
        check(method == InvoicePaymentMethod.QR) { "Only QR payments can be cancelled by a payment provider." }
        check(isGatewayInitialized()) { "QR payment gateway intent has not been initialized." }
        check(status == InvoicePaymentStatus.PENDING || status == InvoicePaymentStatus.EXPIRED) {
            "Only pending or expired QR payments can be cancelled by a payment provider."
        }
        markCancelled(cancelledAt)
        return true
    }

    private fun markCompleted(at: Instant) {
        status = InvoicePaymentStatus.COMPLETED
        completedAt = at
        updatedAt = at
    }

    private fun markCancelled(at: Instant) {
        status = InvoicePaymentStatus.CANCELLED
        cancelledAt = at
        updatedAt = at
    }

    companion object {
        /** Cash handed to finance and recorded as received. */
        fun cash(invoice: CustomerInvoice, amount: Long, reference: String, collectedByUserId: UUID, paidAt: Instant): InvoicePayment =
            completed(invoice, InvoicePaymentMethod.CASH, amount, reference, paidAt).also { it.collectedByUserId = collectedByUserId }

        /** Fees settled from the merchant's own COD at settlement time. */
        fun codOffset(invoice: CustomerInvoice, amount: Long, reference: String, settledAt: Instant): InvoicePayment =
            completed(invoice, InvoicePaymentMethod.COD_OFFSET, amount, reference, settledAt)

        /** Reserves the amount before the gateway is called, so a retry reuses the same payment id and reference. */
        fun qrReservation(
            id: UUID,
            invoice: CustomerInvoice,
            amount: Long,
            reference: String,
            provider: String,
            expiresAt: Instant,
            createdAt: Instant,
        ): InvoicePayment {
            require(expiresAt.isAfter(createdAt)) { "QR payment expiry must be in the future." }
            return pending(id, invoice, InvoicePaymentMethod.QR, amount, reference, createdAt).also {
                it.provider = PaymentProviders.normalize(provider)
                it.qrExpiresAt = expiresAt
            }
        }

        private fun completed(
            invoice: CustomerInvoice,
            method: InvoicePaymentMethod,
            amount: Long,
            reference: String,
            at: Instant,
        ): InvoicePayment = pending(UUID.randomUUID(), invoice, method, amount, reference, at).also { it.markCompleted(at) }

        private fun pending(
            id: UUID,
            invoice: CustomerInvoice,
            method: InvoicePaymentMethod,
            amount: Long,
            reference: String,
            createdAt: Instant,
        ): InvoicePayment {
            require(amount > 0) { "Payment amount must be positive." }
            return InvoicePayment(
                id = id,
                invoice = invoice,
                method = method,
                amount = amount,
                reference = BillingText.required(reference, 100, "reference").uppercase(),
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }
    }
}

object PaymentProviders {
    const val FAKE = "fake"
    const val PAYOS = "payos"

    fun normalize(provider: String): String {
        val normalized = provider.trim().lowercase()
        require(normalized.isNotEmpty()) { "Payment provider is required." }
        require(normalized.length <= 30) { "Payment provider cannot exceed 30 characters." }
        return normalized
    }
}
