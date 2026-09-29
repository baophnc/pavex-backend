package com.pavex.billing.payment

import com.pavex.billing.entity.InvoicePaymentMethod
import com.pavex.billing.entity.InvoicePaymentStatus
import com.pavex.billing.entity.PaymentProviders
import com.pavex.billing.entity.PaymentWebhookReceipt
import com.pavex.billing.entity.PaymentWebhookReceiptStatus
import com.pavex.billing.repository.CustomerInvoiceRepository
import com.pavex.billing.repository.InvoicePaymentRepository
import com.pavex.billing.repository.PaymentWebhookReceiptRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import java.util.UUID

enum class PayOsWebhookOutcome { PROCESSED, DUPLICATE, IGNORED, INVALID, RETRY }

data class PayOsWebhookResult(val outcome: PayOsWebhookOutcome, val code: String, val message: String? = null, val paymentId: UUID? = null)

/**
 * Settles QR payments from verified payOS callbacks (ported from core-api). Every callback is
 * stored as a receipt so a redelivery is acknowledged without paying the invoice twice, and a
 * verified callback that matches nothing is kept for audit instead of failing payOS's URL check.
 */
@Service
class PayOsWebhookService(
    private val properties: PayOsProperties,
    private val receipts: PaymentWebhookReceiptRepository,
    private val payments: InvoicePaymentRepository,
    private val invoices: CustomerInvoiceRepository,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun handle(payload: JsonNode): PayOsWebhookResult {
        if (!properties.enabled || !properties.isConfigured()) {
            return PayOsWebhookResult(PayOsWebhookOutcome.RETRY, "payos_not_configured", "payOS webhook is not configured.")
        }
        val success = payload.get("success")
        val data = payload.get("data")
        val signature = payload.get("signature")
        if (!payload.isObject || success == null || !success.isBoolean || data == null || !data.isObject || signature == null || !signature.isString) {
            return invalid("invalid_webhook", "payOS webhook must include success, data and signature.")
        }
        if (!PayOsSignature.verify(data, signature.asString(), properties.checksumKey)) {
            return invalid("invalid_signature", "payOS webhook signature is invalid.")
        }
        val webhook = WebhookData.read(data)
            ?: return invalid("invalid_webhook_data", "payOS webhook data is missing orderCode, amount, reference, paymentLinkId, currency or code.")

        val succeeded = success.asBoolean() && webhook.code == "00"
        val externalEventId = externalEventId(webhook.paymentLinkId, webhook.reference)
        val receipt = when (val found = receiptFor(externalEventId, if (succeeded) "payment.completed" else "payment.notification", PayOsSignature.payloadHash(data))) {
            is ReceiptLookup.Done -> return found.result
            is ReceiptLookup.Open -> found.receiptId
        }

        return try {
            tx.execute { settle(receipt, webhook, succeeded) }!!
        } catch (_: ObjectOptimisticLockingFailureException) {
            val processed = receipts.findById(receipt).map { it.status == PaymentWebhookReceiptStatus.PROCESSED }.orElse(false)
            if (processed) {
                PayOsWebhookResult(PayOsWebhookOutcome.DUPLICATE, "already_processed")
            } else {
                PayOsWebhookResult(PayOsWebhookOutcome.RETRY, "payment_modified", "Payment state changed while processing the webhook.")
            }
        }
    }

    private fun settle(receiptId: UUID, webhook: WebhookData, succeeded: Boolean): PayOsWebhookResult {
        val receipt = receipts.findById(receiptId).orElseThrow()
        val now = clock.instant()
        fun ignore(code: String, message: String, paymentId: UUID? = null): PayOsWebhookResult {
            receipt.markFailed(message, now, paymentId)
            receipts.saveAndFlush(receipt)
            return PayOsWebhookResult(PayOsWebhookOutcome.IGNORED, code, message, paymentId)
        }

        if (!succeeded) return ignore("payos_unsuccessful_notification", "payOS notification does not represent a successful payment.")
        val payment = payments.findByProviderAndProviderPaymentId(PaymentProviders.PAYOS, webhook.paymentLinkId)
            ?: return ignore("payment_not_found", "No Pavex payment matches this verified payOS notification.")
        if (payment.providerReference != webhook.orderCode.toString()) {
            return ignore("order_code_mismatch", "payOS order code does not match the payment reservation.", payment.id)
        }
        if (payment.amount != webhook.amount) {
            return ignore("payment_amount_mismatch", "payOS amount does not match the payment reservation.", payment.id)
        }
        val invoice = invoices.findWithLinesById(payment.invoice.id)
            ?: return ignore("invoice_not_found", "The invoice for this payment no longer exists.", payment.id)
        if (!invoice.currency.equals(webhook.currency, ignoreCase = true)) {
            return ignore("payment_currency_mismatch", "payOS currency does not match the invoice.", payment.id)
        }
        if (payment.method != InvoicePaymentMethod.QR || !payment.isGatewayInitialized()) {
            return ignore("payment_state_invalid", "The matched payment is not an initialized QR gateway payment.", payment.id)
        }
        if (payment.status == InvoicePaymentStatus.COMPLETED) {
            receipt.markProcessed(payment.id, now)
            receipts.saveAndFlush(receipt)
            return PayOsWebhookResult(PayOsWebhookOutcome.DUPLICATE, "already_processed", paymentId = payment.id)
        }
        if (payment.status == InvoicePaymentStatus.CANCELLED) {
            return ignore("payment_cancelled", "payOS reported payment for a locally cancelled payment intent.", payment.id)
        }
        if (payment.amount > invoice.availablePaymentAmount(now, setOf(payment.id))) {
            return ignore("payment_exceeds_available_balance", "Other reservations or completed payments consume part of the invoice balance.", payment.id)
        }
        val settledAt = webhook.transactionTime() ?: now
        try {
            payment.completeFromProvider(settledAt)
            invoice.applyCompletedPayment(payment.amount, settledAt)
        } catch (exception: IllegalStateException) {
            return ignore("payment_conflict", exception.message ?: "Payment cannot be settled.", payment.id)
        }
        invoices.saveAndFlush(invoice)
        receipt.markProcessed(payment.id, now)
        receipts.saveAndFlush(receipt)
        return PayOsWebhookResult(PayOsWebhookOutcome.PROCESSED, "processed", paymentId = payment.id)
    }

    private fun receiptFor(externalEventId: String, eventType: String, payloadHash: String): ReceiptLookup {
        fun existing(found: PaymentWebhookReceipt): ReceiptLookup = when {
            !found.payloadHash.equals(payloadHash, ignoreCase = true) -> ReceiptLookup.Done(
                invalid("webhook_payload_conflict", "The same payOS event identifier was reused with different payload data."),
            )
            found.status == PaymentWebhookReceiptStatus.PROCESSED ->
                ReceiptLookup.Done(PayOsWebhookResult(PayOsWebhookOutcome.DUPLICATE, "already_processed", paymentId = found.paymentId))
            else -> ReceiptLookup.Open(found.id)
        }

        receipts.findByProviderAndExternalEventId(PaymentProviders.PAYOS, externalEventId)?.let { return existing(it) }
        return try {
            val receipt = tx.execute {
                receipts.saveAndFlush(PaymentWebhookReceipt.create(PaymentProviders.PAYOS, externalEventId, eventType, payloadHash, clock.instant()))
            }!!
            ReceiptLookup.Open(receipt.id)
        } catch (_: DataIntegrityViolationException) {
            existing(receipts.findByProviderAndExternalEventId(PaymentProviders.PAYOS, externalEventId)!!)
        }
    }

    private fun invalid(code: String, message: String) = PayOsWebhookResult(PayOsWebhookOutcome.INVALID, code, message)

    private sealed interface ReceiptLookup {
        data class Done(val result: PayOsWebhookResult) : ReceiptLookup
        data class Open(val receiptId: UUID) : ReceiptLookup
    }

    private data class WebhookData(
        val orderCode: Long,
        val amount: Long,
        val reference: String,
        val paymentLinkId: String,
        val currency: String,
        val code: String,
        val transactionDateTime: String?,
    ) {
        /** payOS sends local Vietnam time without an offset. */
        fun transactionTime(): Instant? = transactionDateTime?.let {
            runCatching { LocalDateTime.parse(it, TRANSACTION_TIME).toInstant(VIETNAM) }.getOrNull()
        }

        companion object {
            private val TRANSACTION_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            private val VIETNAM = ZoneOffset.ofHours(7)

            fun read(data: JsonNode): WebhookData? {
                fun text(name: String) = data.get(name)?.takeIf { it.isString }?.asString()?.takeIf { it.isNotBlank() }
                val orderCode = data.get("orderCode")?.takeIf { it.isIntegralNumber }?.asLong() ?: return null
                val amount = data.get("amount")?.takeIf { it.isNumber }?.asLong() ?: return null
                return WebhookData(
                    orderCode = orderCode,
                    amount = amount,
                    reference = text("reference") ?: return null,
                    paymentLinkId = text("paymentLinkId") ?: return null,
                    currency = text("currency") ?: return null,
                    code = text("code") ?: return null,
                    transactionDateTime = text("transactionDateTime"),
                )
            }
        }
    }

    companion object {
        fun externalEventId(paymentLinkId: String, reference: String): String {
            val value = "${paymentLinkId.trim()}:${reference.trim()}"
            if (value.length <= 200) return value
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
        }
    }
}
