package com.pavex.billing.payment

import com.pavex.billing.entity.PaymentProviders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * payOS payment links (ported from core-api). Every response's data is signature-checked before
 * it is trusted; 429/5xx and network errors are transient, everything else is permanent.
 */
@Component
class PayOsPaymentGateway(
    private val properties: PayOsProperties,
    private val objectMapper: ObjectMapper,
) : PaymentGateway {
    private val client: RestClient by lazy {
        val http = HttpClient.newBuilder().connectTimeout(properties.timeout).build()
        RestClient.builder()
            .baseUrl(properties.apiBaseUrl)
            .requestFactory(JdkClientHttpRequestFactory(http).apply { setReadTimeout(properties.timeout) })
            .build()
    }

    override val provider: String = PaymentProviders.PAYOS

    override fun createPayment(request: PaymentGatewayCreateRequest): PaymentGatewayCreateResult {
        ensureConfigured()
        if (!request.currency.equals("VND", ignoreCase = true)) {
            throw permanent("payos_currency_unsupported", "payOS payments currently require VND.")
        }
        if (request.amount <= 0) throw permanent("payos_amount_invalid", "payOS payment amount must be a positive VND amount.")
        val expiredAt = request.expiresAt.epochSecond
        if (expiredAt <= 0 || expiredAt > Int.MAX_VALUE) {
            throw permanent("payos_expiry_invalid", "payOS payment expiry must fit the provider Unix timestamp range.")
        }

        val orderCode = orderCode(request.paymentId)
        val description = description(orderCode)
        val body = mapOf(
            "orderCode" to orderCode,
            "amount" to request.amount,
            "description" to description,
            "cancelUrl" to properties.cancelUrl,
            "returnUrl" to properties.returnUrl,
            "expiredAt" to expiredAt.toInt(),
            "signature" to PayOsSignature.paymentRequestSignature(
                request.amount,
                properties.cancelUrl,
                description,
                orderCode,
                properties.returnUrl,
                properties.checksumKey,
            ),
        )
        val data = send { client.post().uri("/v2/payment-requests").contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(body)) }

        if (data.path("orderCode").asLong() != orderCode || data.path("amount").asLong() != request.amount) {
            throw permanent("payos_response_mismatch", "payOS returned payment data that does not match the request.")
        }
        if (!data.path("currency").asString("").equals("VND", ignoreCase = true)) {
            throw permanent("payos_response_mismatch", "payOS returned an unexpected payment currency.")
        }
        val paymentLinkId = data.path("paymentLinkId").asString("")
        val qrCode = data.path("qrCode").asString("")
        if (paymentLinkId.isBlank() || qrCode.isBlank()) {
            throw permanent("payos_invalid_response", "payOS response is missing payment link or QR data.")
        }
        return PaymentGatewayCreateResult(
            providerPaymentId = paymentLinkId,
            providerReference = orderCode.toString(),
            qrPayload = qrCode,
            checkoutUrl = data.path("checkoutUrl").asString("").ifBlank { null },
            expiresAt = request.expiresAt,
        )
    }

    override fun cancelPayment(request: PaymentGatewayCancelRequest) {
        ensureConfigured()
        val body = mapOf("cancellationReason" to (request.reason?.trim()?.ifBlank { null } ?: "Pavex invoice cancelled"))
        val data = send {
            client.post()
                .uri("/v2/payment-requests/{id}/cancel", request.providerPaymentId.trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
        }
        if (data.path("id").asString("") != request.providerPaymentId) {
            throw permanent("payos_response_mismatch", "payOS returned a different payment link id while cancelling.")
        }
        when (status(data.path("status").asString(""))) {
            PaymentGatewayPaymentStatus.CANCELLED -> Unit
            PaymentGatewayPaymentStatus.PAID ->
                throw permanent("payos_payment_already_paid", "payOS reports that this payment was already paid and cannot be cancelled.")
            else -> throw permanent("payos_cancel_not_confirmed", "payOS did not confirm the payment link as cancelled.")
        }
    }

    override fun getPayment(request: PaymentGatewayLookupRequest): PaymentGatewayLookupResult {
        ensureConfigured()
        val data = send { client.get().uri("/v2/payment-requests/{id}", request.providerPaymentId.trim()) }
        if (data.path("id").asString("") != request.providerPaymentId) {
            throw permanent("payos_response_mismatch", "payOS returned a different payment link id.")
        }
        val orderCode = data.path("orderCode").asLong().toString()
        if (request.providerReference != null && orderCode != request.providerReference) {
            throw permanent("payos_response_mismatch", "payOS returned a different order code.")
        }
        val amount = data.path("amount").asLong(-1)
        val amountPaid = data.path("amountPaid").asLong(-1)
        if (amount < 0 || amountPaid < 0) throw permanent("payos_invalid_response", "payOS returned invalid payment amounts.")
        return PaymentGatewayLookupResult(status(data.path("status").asString("")), request.providerPaymentId, orderCode, amount, amountPaid)
    }

    /** Sends the request and returns the verified `data` object of the payOS envelope. */
    private fun send(request: () -> RestClient.RequestHeadersSpec<*>): JsonNode {
        val response: ResponseEntity<String> = try {
            request()
                .header("x-client-id", properties.clientId)
                .header("x-api-key", properties.apiKey)
                .retrieve()
                .onStatus({ true }) { _, _ -> }
                .toEntity(String::class.java)
        } catch (exception: ResourceAccessException) {
            throw PaymentGatewayException("payos_unavailable", "Could not reach payOS.", transient = true, cause = exception)
        } catch (exception: RestClientException) {
            throw PaymentGatewayException("payos_unavailable", "payOS request failed.", transient = true, cause = exception)
        }

        val statusCode = response.statusCode
        val envelope = runCatching { objectMapper.readTree(response.body ?: "") }.getOrNull()
        if (envelope == null || !envelope.isObject) {
            throw PaymentGatewayException("payos_invalid_response", "payOS returned malformed JSON.", transient = isTransient(statusCode))
        }
        if (!statusCode.is2xxSuccessful) {
            val detail = envelope.path("desc").asString("").ifBlank { "payOS returned HTTP ${statusCode.value()}." }
            when {
                isTransient(statusCode) -> throw PaymentGatewayException("payos_unavailable", detail, transient = true)
                statusCode.value() == 401 || statusCode.value() == 403 -> throw permanent("payos_auth_failed", detail)
                else -> throw permanent("payos_request_rejected", detail)
            }
        }
        if (envelope.path("code").asString("") != "00") {
            throw permanent("payos_request_rejected", envelope.path("desc").asString("").ifBlank { "payOS rejected the request." })
        }
        val data = envelope.get("data")
        if (data == null || !data.isObject) throw permanent("payos_invalid_response", "payOS response did not contain signed data.")
        if (!PayOsSignature.verify(data, envelope.path("signature").asString(""), properties.checksumKey)) {
            throw permanent("payos_invalid_signature", "payOS response signature is invalid.")
        }
        return data
    }

    private fun ensureConfigured() {
        if (!properties.enabled || !properties.isConfigured()) {
            throw PaymentGatewayException("payos_not_configured", "payOS payment gateway is not configured.", transient = true)
        }
    }

    private fun isTransient(status: HttpStatusCode): Boolean = status.value() == 429 || status.is5xxServerError

    private fun permanent(code: String, message: String) = PaymentGatewayException(code, message, transient = false)

    companion object {
        /** A stable 15-digit payOS order code derived from the payment id, so retries reuse it. */
        fun orderCode(paymentId: UUID): Long {
            val bytes = ByteBuffer.allocate(16).putLong(paymentId.mostSignificantBits).putLong(paymentId.leastSignificantBits).array()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            val value = ByteBuffer.wrap(hash, 0, 8).long.toULong()
            return (value % 900_000_000_000_000uL).toLong() + 100_000_000_000_000L
        }

        fun description(orderCode: Long): String = "PVX%06d".format(orderCode % 1_000_000)

        fun status(value: String): PaymentGatewayPaymentStatus = when (value.trim().uppercase()) {
            "PENDING" -> PaymentGatewayPaymentStatus.PENDING
            "PROCESSING" -> PaymentGatewayPaymentStatus.PROCESSING
            "PAID" -> PaymentGatewayPaymentStatus.PAID
            "CANCELLED" -> PaymentGatewayPaymentStatus.CANCELLED
            else -> PaymentGatewayPaymentStatus.UNKNOWN
        }
    }
}
