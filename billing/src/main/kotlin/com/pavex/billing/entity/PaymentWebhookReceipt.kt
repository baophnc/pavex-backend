package com.pavex.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class PaymentWebhookReceiptStatus { RECEIVED, PROCESSED, FAILED }

/** One verified provider callback; the same event id with a different payload is rejected. */
@Entity
@Table(name = "payment_webhook_receipts", schema = "billing")
class PaymentWebhookReceipt(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 30)
    var provider: String = "",
    @Column(name = "external_event_id", nullable = false, length = 200)
    var externalEventId: String = "",
    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String = "",
    @Column(name = "payload_hash", nullable = false, length = 128)
    var payloadHash: String = "",
    @Column(name = "payment_id")
    var paymentId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: PaymentWebhookReceiptStatus = PaymentWebhookReceiptStatus.RECEIVED,
    @Column(name = "received_at", nullable = false)
    var receivedAt: Instant = Instant.EPOCH,
    @Column(name = "processed_at")
    var processedAt: Instant? = null,
    @Column(name = "failed_at")
    var failedAt: Instant? = null,
    @Column(name = "failure_reason", length = 1000)
    var failureReason: String? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    fun markProcessed(paymentId: UUID, processedAt: Instant) {
        this.paymentId = paymentId
        status = PaymentWebhookReceiptStatus.PROCESSED
        this.processedAt = processedAt
        failedAt = null
        failureReason = null
        updatedAt = processedAt
    }

    fun markFailed(reason: String, failedAt: Instant, paymentId: UUID? = null) {
        this.paymentId = paymentId ?: this.paymentId
        status = PaymentWebhookReceiptStatus.FAILED
        this.failedAt = failedAt
        failureReason = BillingText.required(reason, 1000, "reason")
        updatedAt = failedAt
    }

    companion object {
        fun create(provider: String, externalEventId: String, eventType: String, payloadHash: String, receivedAt: Instant) =
            PaymentWebhookReceipt(
                provider = PaymentProviders.normalize(provider),
                externalEventId = BillingText.required(externalEventId, 200, "externalEventId"),
                eventType = BillingText.required(eventType, 100, "eventType"),
                payloadHash = BillingText.required(payloadHash, 128, "payloadHash").uppercase(),
                receivedAt = receivedAt,
                updatedAt = receivedAt,
            )
    }
}
