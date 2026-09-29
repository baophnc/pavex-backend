package com.pavex.billing.service

import com.pavex.billing.client.PartnersClient
import com.pavex.billing.dto.InvoiceListItemResponse
import com.pavex.billing.dto.InvoiceResponse
import com.pavex.billing.dto.QrPaymentIntentResponse
import com.pavex.billing.entity.CustomerInvoice
import com.pavex.billing.entity.InvoicePayment
import com.pavex.billing.entity.InvoicePaymentMethod
import com.pavex.billing.entity.InvoicePaymentStatus
import com.pavex.billing.entity.InvoiceStatus
import com.pavex.billing.entity.PaymentProviders
import com.pavex.billing.exception.BillingApiException
import com.pavex.billing.exception.BillingConflictException
import com.pavex.billing.exception.BillingNotFoundException
import com.pavex.billing.exception.BillingUnavailableException
import com.pavex.billing.exception.InvalidBillingRequestException
import com.pavex.billing.payment.PaymentGateway
import com.pavex.billing.payment.PaymentGatewayCancelRequest
import com.pavex.billing.payment.PaymentGatewayCreateRequest
import com.pavex.billing.payment.PaymentGatewayException
import com.pavex.billing.payment.PaymentGatewayResolver
import com.pavex.billing.repository.CustomerInvoiceRepository
import com.pavex.billing.repository.InvoicePaymentRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Merchant invoices and their payments (ported from core-api's CustomerInvoiceService).
 * External gateway calls happen outside database transactions: a QR payment is reserved and
 * committed first, so a retry reuses the same payment id and reference.
 */
@Service
class InvoiceService(
    private val invoices: CustomerInvoiceRepository,
    private val payments: InvoicePaymentRepository,
    private val partners: PartnersClient,
    private val gateways: PaymentGatewayResolver,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
    @Value("\${billing.payments.qr.expires-in:PT15M}") private val qrExpiresIn: Duration,
) {
    private val tx = TransactionTemplate(transactionManager)
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    fun listMine(userId: UUID, status: InvoiceStatus?): List<InvoiceListItemResponse> {
        val merchantId = partners.requireMerchantId(userId)
        return list(merchantId, status)
    }

    fun getMine(userId: UUID, invoiceId: UUID): InvoiceResponse {
        val merchantId = partners.requireMerchantId(userId)
        return inReadTx { InvoiceResponse.from(loadOwned(invoiceId, merchantId)) }
    }

    fun list(merchantId: UUID?, status: InvoiceStatus?): List<InvoiceListItemResponse> = inReadTx {
        invoices.search(merchantId, status, PageRequest.of(0, LIST_LIMIT)).map(InvoiceListItemResponse::from)
    }

    fun get(invoiceId: UUID): InvoiceResponse = inReadTx { InvoiceResponse.from(load(invoiceId)) }

    fun recordCashPayment(invoiceId: UUID, amount: Long, reference: String?, actorUserId: UUID): InvoiceResponse = inTx {
        val invoice = load(invoiceId)
        val now = clock.instant()
        invoice.expirePendingQrPayments(now)
        if (amount <= 0 || amount > invoice.availablePaymentAmount(now)) {
            throw InvalidBillingRequestException("invalid_payment_amount", "Payment amount cannot exceed the unreserved invoice balance.")
        }
        val paymentReference = reference?.trim()?.ifEmpty { null }
            ?: "CASH-${invoice.invoiceNumber}-${compactId(UUID.randomUUID())}".take(100)
        domain("invoice_not_payable") {
            invoice.addPayment(InvoicePayment.cash(invoice, amount, paymentReference, actorUserId, now))
            invoice.applyCompletedPayment(amount, now)
        }
        InvoiceResponse.from(invoices.saveAndFlush(invoice))
    }

    fun createQrPayment(userId: UUID, invoiceId: UUID, requestedAmount: Long?): QrPaymentIntentResponse {
        val merchantId = partners.requireMerchantId(userId)
        val paymentId = inTx { reserveQrPayment(loadOwned(invoiceId, merchantId), requestedAmount) }
        return ensureGatewayIntent(paymentId)
    }

    /** Settles a fake-gateway QR payment. Real providers are settled only by their verified callback. */
    fun confirmQrPayment(paymentId: UUID): InvoiceResponse {
        val result = inTx {
            val payment = payments.findById(paymentId).orElseThrow { notFound("payment_not_found", "Payment was not found.") }
            val invoice = load(payment.invoice.id)
            val now = clock.instant()
            when {
                payment.method != InvoicePaymentMethod.QR ->
                    throw InvalidBillingRequestException("invalid_payment_method", "Only QR payment intents require confirmation.")
                payment.status == InvoicePaymentStatus.COMPLETED -> ConfirmResult(InvoiceResponse.from(invoice), null)
                payment.provider != PaymentProviders.FAKE -> throw BillingConflictException(
                    "provider_confirmation_required",
                    "External gateway payments must be confirmed by a verified provider callback.",
                )
                !payment.isGatewayInitialized() -> throw BillingConflictException(
                    "payment_gateway_pending",
                    "Payment gateway intent has not finished initializing. Retry payment creation first.",
                )
                else -> {
                    invoice.expirePendingQrPayments(now)
                    val refusal = when {
                        payment.status == InvoicePaymentStatus.EXPIRED -> BillingConflictException("qr_payment_expired", "The QR payment has expired.")
                        payment.status != InvoicePaymentStatus.PENDING -> BillingConflictException("payment_not_pending", "The payment is no longer pending.")
                        payment.amount > invoice.availablePaymentAmount(now, setOf(payment.id)) -> BillingConflictException(
                            "payment_exceeds_available_balance",
                            "Other pending payments reserve part of the invoice balance.",
                        )
                        else -> null
                    }
                    if (refusal == null) {
                        domain("payment_conflict") {
                            payment.complete(now)
                            invoice.applyCompletedPayment(payment.amount, now)
                        }
                    }
                    // Keep any expiry that was just recorded, even when the confirmation is refused.
                    ConfirmResult(InvoiceResponse.from(invoices.saveAndFlush(invoice)), refusal)
                }
            }
        }
        result.refusal?.let { throw it }
        return result.invoice
    }

    /** Cancels an unpaid invoice, closing any open gateway payment links first. */
    fun cancel(invoiceId: UUID, actorUserId: UUID, reason: String): InvoiceResponse {
        val openGatewayPayments = inReadTx {
            val invoice = load(invoiceId)
            if (invoice.status == InvoiceStatus.CANCELLED) return@inReadTx null
            if (invoice.paidAmount > 0) {
                throw BillingConflictException("invoice_cancel_conflict", "An invoice with completed payments cannot be cancelled.")
            }
            invoice.payments
                .filter {
                    it.method == InvoicePaymentMethod.QR && it.isGatewayInitialized() &&
                        (it.status == InvoicePaymentStatus.PENDING || it.status == InvoicePaymentStatus.EXPIRED)
                }
                .map { Triple(it.id, it.provider!!, it.providerPaymentId!!) }
        } ?: return get(invoiceId)

        openGatewayPayments.forEach { (paymentId, provider, providerPaymentId) ->
            val gateway = gateway { gateways.get(provider) }
            callGateway { gateway.cancelPayment(PaymentGatewayCancelRequest(paymentId, providerPaymentId, reason)) }
            inTx {
                val payment = payments.findById(paymentId).orElseThrow()
                payment.cancelFromProvider(clock.instant())
                payments.saveAndFlush(payment)
            }
        }

        return inTx {
            val invoice = load(invoiceId)
            domain("invoice_cancel_conflict") { invoice.cancel(actorUserId, reason, clock.instant()) }
            InvoiceResponse.from(invoices.saveAndFlush(invoice))
        }
    }

    private fun reserveQrPayment(invoice: CustomerInvoice, requestedAmount: Long?): UUID {
        if (!invoice.isPayable()) throw BillingConflictException("invoice_not_payable", "The invoice cannot accept more payments.")
        val now = clock.instant()
        invoice.expirePendingQrPayments(now)
        val activeQr = invoice.payments
            .filter { it.method == InvoicePaymentMethod.QR && it.reservesBalanceAt(now) }
            .sortedByDescending { it.createdAt }

        // Reuse a live code for the same amount instead of reserving the balance twice.
        val reusable = if (requestedAmount != null) {
            activeQr.firstOrNull { it.amount == requestedAmount && it.amount <= invoice.availablePaymentAmount(now, setOf(it.id)) }
        } else {
            activeQr.singleOrNull()?.takeIf {
                invoice.availablePaymentAmount(now) <= 0 && it.amount <= invoice.availablePaymentAmount(now, setOf(it.id))
            }
        }
        if (reusable != null) {
            invoices.saveAndFlush(invoice)
            return reusable.id
        }

        val available = invoice.availablePaymentAmount(now)
        val amount = requestedAmount ?: available
        if (amount <= 0 || amount > available) {
            throw InvalidBillingRequestException("invalid_payment_amount", "Payment amount cannot exceed the unreserved invoice balance.")
        }
        val gateway = gateway { gateways.default() }
        val paymentId = UUID.randomUUID()
        val payment = InvoicePayment.qrReservation(
            id = paymentId,
            invoice = invoice,
            amount = amount,
            reference = "${invoice.invoiceNumber}-${compactId(paymentId).take(8)}",
            provider = gateway.provider,
            expiresAt = now.plus(qrExpiresIn),
            createdAt = now,
        )
        invoice.addPayment(payment)
        invoices.saveAndFlush(invoice)
        return paymentId
    }

    private fun ensureGatewayIntent(paymentId: UUID): QrPaymentIntentResponse {
        val state = inReadTx {
            val payment = payments.findById(paymentId).orElseThrow { notFound("payment_not_found", "Payment was not found.") }
            if (payment.isGatewayInitialized()) {
                IntentState.Ready(qrResponse(payment))
            } else {
                val invoice = payment.invoice
                IntentState.NeedsGateway(
                    request = PaymentGatewayCreateRequest(
                        paymentId = payment.id,
                        invoiceId = invoice.id,
                        invoiceNumber = invoice.invoiceNumber,
                        amount = payment.amount,
                        currency = invoice.currency,
                        reference = payment.reference,
                        expiresAt = requireNotNull(payment.qrExpiresAt),
                    ),
                    provider = requireNotNull(payment.provider),
                )
            }
        }
        val (request, provider) = when (state) {
            is IntentState.Ready -> return state.response
            is IntentState.NeedsGateway -> state
        }
        val gateway = gateway { gateways.get(provider) }

        val intent = try {
            gateway.createPayment(request)
        } catch (exception: PaymentGatewayException) {
            if (!exception.transient) {
                inTx {
                    val payment = payments.findById(paymentId).orElseThrow()
                    payment.cancel(clock.instant())
                    payments.saveAndFlush(payment)
                }
                throw BillingConflictException(exception.code, exception.message ?: "The payment gateway rejected the request.")
            }
            throw BillingUnavailableException(exception.code, exception.message ?: "The payment gateway is unavailable.")
        }

        return inTx {
            val payment = payments.findById(paymentId).orElseThrow()
            if (!payment.isGatewayInitialized()) {
                domain("payment_gateway_invalid_response") {
                    payment.attachGatewayIntent(
                        intent.providerPaymentId,
                        intent.providerReference,
                        intent.qrPayload,
                        intent.checkoutUrl,
                        intent.expiresAt,
                        clock.instant(),
                    )
                }
                payments.saveAndFlush(payment)
            }
            qrResponse(payment)
        }
    }

    private fun qrResponse(payment: InvoicePayment) = QrPaymentIntentResponse(
        paymentId = payment.id,
        invoiceId = payment.invoice.id,
        amount = payment.amount,
        currency = payment.invoice.currency,
        reference = payment.reference,
        provider = requireNotNull(payment.provider),
        providerPaymentId = requireNotNull(payment.providerPaymentId),
        providerReference = payment.providerReference,
        checkoutUrl = payment.checkoutUrl,
        qrPayload = requireNotNull(payment.qrPayload),
        expiresAt = requireNotNull(payment.qrExpiresAt),
    )

    private fun load(invoiceId: UUID): CustomerInvoice =
        invoices.findWithLinesById(invoiceId) ?: throw notFound("invoice_not_found", "Invoice was not found.")

    /** Another merchant's invoice is reported as not found. */
    private fun loadOwned(invoiceId: UUID, merchantId: UUID): CustomerInvoice =
        load(invoiceId).takeIf { it.merchantId == merchantId } ?: throw notFound("invoice_not_found", "Invoice was not found.")

    private fun gateway(block: () -> PaymentGateway): PaymentGateway = try {
        block()
    } catch (exception: IllegalStateException) {
        throw BillingUnavailableException("payment_gateway_not_configured", exception.message ?: "Payment gateway is not configured.")
    }

    private fun callGateway(block: () -> Unit) = try {
        block()
    } catch (exception: PaymentGatewayException) {
        if (exception.transient) throw BillingUnavailableException(exception.code, exception.message ?: "The payment gateway is unavailable.")
        throw BillingConflictException(exception.code, exception.message ?: "The payment gateway rejected the request.")
    }

    private fun <T> inTx(block: () -> T): T = tx.execute { block() } as T

    private fun <T> inReadTx(block: () -> T): T = readTx.execute { block() } as T

    private data class ConfirmResult(val invoice: InvoiceResponse, val refusal: BillingApiException?)

    private sealed interface IntentState {
        data class Ready(val response: QrPaymentIntentResponse) : IntentState
        data class NeedsGateway(val request: PaymentGatewayCreateRequest, val provider: String) : IntentState
    }

    companion object {
        private const val LIST_LIMIT = 200

        fun compactId(id: UUID): String = id.toString().replace("-", "").uppercase()

        private fun notFound(code: String, message: String) = BillingNotFoundException(code, message)

        /** Domain rule violations become 409s (state) or 400s (input) with a stable code. */
        fun <T> domain(conflictCode: String, block: () -> T): T = try {
            block()
        } catch (exception: IllegalStateException) {
            throw BillingConflictException(conflictCode, exception.message ?: "The operation conflicts with the current state.")
        } catch (exception: IllegalArgumentException) {
            throw InvalidBillingRequestException("invalid_request", exception.message ?: "The request is invalid.")
        }
    }
}
