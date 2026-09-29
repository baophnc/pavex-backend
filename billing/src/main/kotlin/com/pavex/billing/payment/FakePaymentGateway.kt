package com.pavex.billing.payment

import com.pavex.billing.entity.PaymentProviders
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Local-development gateway: returns a signed QR payload and is settled by an admin through the
 * confirm endpoint. State is in memory, so it resets on restart.
 */
@Component
class FakePaymentGateway(
    private val qrCodec: BillingQrCodec,
) : PaymentGateway {
    private data class Entry(val request: PaymentGatewayCreateRequest, val result: PaymentGatewayCreateResult, val status: PaymentGatewayPaymentStatus)

    private val payments = ConcurrentHashMap<UUID, Entry>()

    override val provider: String = PaymentProviders.FAKE

    override fun createPayment(request: PaymentGatewayCreateRequest): PaymentGatewayCreateResult {
        if (request.amount <= 0) throw PaymentGatewayException("invalid_gateway_request", "Payment amount must be positive.", transient = false)
        val entry = payments.computeIfAbsent(request.paymentId) {
            Entry(
                request = request,
                result = PaymentGatewayCreateResult(
                    providerPaymentId = "fake-${request.paymentId.toString().replace("-", "")}",
                    providerReference = request.reference,
                    qrPayload = qrCodec.encode(
                        request.paymentId,
                        request.invoiceNumber,
                        request.amount,
                        request.currency,
                        request.reference,
                        request.expiresAt,
                    ),
                    checkoutUrl = null,
                    expiresAt = request.expiresAt,
                ),
                status = PaymentGatewayPaymentStatus.PENDING,
            )
        }
        if (entry.request != request) {
            throw PaymentGatewayException(
                "gateway_idempotency_conflict",
                "The same payment id was reused with different gateway request data.",
                transient = false,
            )
        }
        return entry.result
    }

    override fun cancelPayment(request: PaymentGatewayCancelRequest) {
        payments.computeIfPresent(request.paymentId) { _, entry ->
            if (entry.result.providerPaymentId != request.providerPaymentId) {
                throw PaymentGatewayException("gateway_payment_mismatch", "Provider payment id does not match.", transient = false)
            }
            entry.copy(status = PaymentGatewayPaymentStatus.CANCELLED)
        }
    }

    override fun getPayment(request: PaymentGatewayLookupRequest): PaymentGatewayLookupResult {
        val entry = payments[request.paymentId]
            ?: return PaymentGatewayLookupResult(PaymentGatewayPaymentStatus.UNKNOWN, request.providerPaymentId, request.providerReference, 0, 0)
        return PaymentGatewayLookupResult(
            status = entry.status,
            providerPaymentId = entry.result.providerPaymentId,
            providerReference = entry.result.providerReference,
            amount = entry.request.amount,
            amountPaid = if (entry.status == PaymentGatewayPaymentStatus.PAID) entry.request.amount else 0,
        )
    }
}
