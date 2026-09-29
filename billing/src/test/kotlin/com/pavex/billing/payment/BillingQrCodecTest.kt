package com.pavex.billing.payment

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BillingQrCodecTest {
    private val codec = BillingQrCodec("PVX", "local-development-billing-qr-signing-key-0001")

    @Test
    fun `payload fits a version-10 QR code and is signed`() {
        val paymentId = UUID.randomUUID()
        val payload = codec.encode(
            paymentId,
            "INV-20260925-ABCDEF12",
            9_999_999_999,
            "VND",
            "INV-20260925-ABCDEF12-1234ABCD",
            Instant.parse("2026-09-25T05:15:00Z"),
        )

        assertTrue(payload.startsWith("PVXBILL1|PVX|"))
        assertTrue(payload.length <= 200, "payload length ${payload.length}")
        val tampered = codec.encode(paymentId, "INV-20260925-ABCDEF12", 1, "VND", "INV-20260925-ABCDEF12-1234ABCD", Instant.parse("2026-09-25T05:15:00Z"))
        assertNotEquals(payload.substringAfterLast('|'), tampered.substringAfterLast('|'))
    }
}
