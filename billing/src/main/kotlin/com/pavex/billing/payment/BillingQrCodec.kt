package com.pavex.billing.payment

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signed QR payload for the fake gateway, so a scanned code cannot be forged in development.
 * Kept under ~160 characters so it fits a version-10 QR code; the HMAC is truncated to 128 bits.
 */
@Component
class BillingQrCodec(
    @Value("\${billing.payments.qr.issuer:PVX}") private val issuer: String,
    @Value("\${billing.payments.qr.signing-key}") private val signingKey: String,
) {
    fun encode(
        paymentId: UUID,
        invoiceNumber: String,
        amount: Long,
        currency: String,
        reference: String,
        expiresAt: Instant,
    ): String {
        check(signingKey.toByteArray().size >= 32) { "Billing QR signing key must be at least 32 bytes." }
        val body = listOf(
            "PVXBILL1",
            segment(issuer),
            paymentId.toString().replace("-", ""),
            segment(invoiceNumber),
            amount.toString(),
            segment(currency),
            segment(reference),
            expiresAt.epochSecond.toString(),
        ).joinToString("|")
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(signingKey.toByteArray(), "HmacSHA256")) }
        return "$body|${HexFormat.of().withUpperCase().formatHex(mac.doFinal(body.toByteArray()).copyOf(16))}"
    }

    private fun segment(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty() && '|' !in normalized) { "QR segment is invalid." }
        return normalized
    }
}
