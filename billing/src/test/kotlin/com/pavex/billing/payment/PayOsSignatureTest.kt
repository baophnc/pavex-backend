package com.pavex.billing.payment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PayOsSignatureTest {
    private val key = "checksum-key-123"
    private val json = JsonMapper.builder().build()

    @Test
    fun `payment request signature signs the documented fields in order`() {
        val signature = PayOsSignature.paymentRequestSignature(125_000, "https://pavex.test/cancel", "PVX123456", 345678901234567, "https://pavex.test/return", key)

        assertEquals(
            hmac("amount=125000&cancelUrl=https://pavex.test/cancel&description=PVX123456&orderCode=345678901234567&returnUrl=https://pavex.test/return"),
            signature,
        )
    }

    @Test
    fun `signed data uses sorted top-level fields and canonical nested JSON`() {
        val data = json.readTree(
            """{"orderCode":123,"amount":125000,"description":"PVX000123","accountName":null,"meta":{"z":1,"a":"x"},"paid":true}""",
        )

        assertEquals(
            "accountName=&amount=125000&description=PVX000123&meta={\"a\":\"x\",\"z\":1}&orderCode=123&paid=true",
            PayOsSignature.canonicalize(data),
        )
    }

    @Test
    fun `verification accepts the provider signature and rejects tampering`() {
        val data = json.readTree("""{"orderCode":123,"amount":125000,"paymentLinkId":"payos-link-0031","code":"00"}""")
        val signature = PayOsSignature.sign(data, key)

        assertTrue(PayOsSignature.verify(data, signature.uppercase(), key))
        assertFalse(PayOsSignature.verify(json.readTree("""{"orderCode":123,"amount":1,"paymentLinkId":"payos-link-0031","code":"00"}"""), signature, key))
        assertFalse(PayOsSignature.verify(data, "not-hex", key))
    }

    @Test
    fun `order codes are stable 15-digit numbers and descriptions fit payOS limits`() {
        val paymentId = UUID.fromString("8a278d5c-d4d1-4d51-911a-187df0fb5db1")
        val orderCode = PayOsPaymentGateway.orderCode(paymentId)

        assertEquals(orderCode, PayOsPaymentGateway.orderCode(paymentId))
        assertEquals(15, orderCode.toString().length)
        val description = PayOsPaymentGateway.description(orderCode)
        assertTrue(description.matches(Regex("^PVX[0-9]{6}$")))
    }

    private fun hmac(value: String): String =
        HexFormat.of().formatHex(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }.doFinal(value.toByteArray()))
}
