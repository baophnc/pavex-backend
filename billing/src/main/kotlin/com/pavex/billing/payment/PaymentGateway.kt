package com.pavex.billing.payment

import com.pavex.billing.entity.PaymentProviders
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

data class PaymentGatewayCreateRequest(
    val paymentId: UUID,
    val invoiceId: UUID,
    val invoiceNumber: String,
    val amount: Long,
    val currency: String,
    val reference: String,
    val expiresAt: Instant,
)

data class PaymentGatewayCreateResult(
    val providerPaymentId: String,
    val providerReference: String?,
    val qrPayload: String,
    val checkoutUrl: String?,
    val expiresAt: Instant,
)

data class PaymentGatewayCancelRequest(val paymentId: UUID, val providerPaymentId: String, val reason: String?)

enum class PaymentGatewayPaymentStatus { PENDING, PROCESSING, PAID, CANCELLED, UNKNOWN }

data class PaymentGatewayLookupRequest(val paymentId: UUID, val providerPaymentId: String, val providerReference: String?)

data class PaymentGatewayLookupResult(
    val status: PaymentGatewayPaymentStatus,
    val providerPaymentId: String,
    val providerReference: String?,
    val amount: Long,
    val amountPaid: Long,
)

/** A QR payment provider. Implementations must be idempotent per payment id. */
interface PaymentGateway {
    val provider: String
    fun createPayment(request: PaymentGatewayCreateRequest): PaymentGatewayCreateResult
    fun cancelPayment(request: PaymentGatewayCancelRequest)
    fun getPayment(request: PaymentGatewayLookupRequest): PaymentGatewayLookupResult
}

/** [transient] failures may succeed on retry; permanent ones need a new request. */
class PaymentGatewayException(
    val code: String,
    message: String,
    val transient: Boolean = true,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

@Component
class PaymentGatewayResolver(
    gateways: List<PaymentGateway>,
    @Value("\${billing.payments.default-provider:fake}") private val defaultProvider: String,
) {
    private val byProvider: Map<String, PaymentGateway> = gateways
        .groupBy { PaymentProviders.normalize(it.provider) }
        .mapValues { (provider, registered) ->
            registered.singleOrNull() ?: error("Payment gateway provider '$provider' was registered more than once.")
        }

    fun default(): PaymentGateway = get(defaultProvider)

    fun get(provider: String): PaymentGateway {
        val normalized = PaymentProviders.normalize(provider)
        return byProvider[normalized] ?: throw IllegalStateException("Payment gateway provider '$normalized' is not registered.")
    }
}
