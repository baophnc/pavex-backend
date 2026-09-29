package com.pavex.billing.payment

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * payOS HMAC-SHA256 signatures (ported from core-api). Signed data is the object's top-level
 * fields sorted by name as `key=value&...`; nested values use canonical, key-sorted JSON.
 */
object PayOsSignature {
    private val json = JsonMapper.builder().build()

    fun paymentRequestSignature(
        amount: Long,
        cancelUrl: String,
        description: String,
        orderCode: Long,
        returnUrl: String,
        checksumKey: String,
    ): String = hmacHex(
        "amount=$amount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl",
        checksumKey,
    )

    fun canonicalize(data: JsonNode): String {
        require(data.isObject) { "payOS signed data must be a JSON object." }
        return data.propertyNames().sorted().joinToString("&") { name -> "$name=${topLevelValue(data.get(name))}" }
    }

    fun sign(data: JsonNode, checksumKey: String): String = hmacHex(canonicalize(data), checksumKey)

    fun verify(data: JsonNode, signature: String, checksumKey: String): Boolean {
        if (signature.isBlank() || checksumKey.isBlank() || !data.isObject) return false
        val actual = runCatching { HexFormat.of().parseHex(signature.trim().lowercase()) }.getOrNull() ?: return false
        return MessageDigest.isEqual(actual, hmac(canonicalize(data), checksumKey))
    }

    fun payloadHash(data: JsonNode): String =
        HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalize(data).toByteArray()))

    private fun topLevelValue(value: JsonNode?): String = when {
        value == null || value.isNull || value.isMissingNode -> ""
        value.isString -> value.asString()
        value.isArray || value.isObject -> canonicalJson(value)
        else -> value.toString()
    }

    private fun canonicalJson(value: JsonNode): String = when {
        value.isObject -> value.propertyNames().sorted()
            .joinToString(",", "{", "}") { name -> "${json.writeValueAsString(name)}:${canonicalJson(value.get(name))}" }
        value.isArray -> value.values().joinToString(",", "[", "]") { canonicalJson(it) }
        value.isString -> json.writeValueAsString(value.asString())
        value.isNull || value.isMissingNode -> "null"
        else -> value.toString()
    }

    private fun hmacHex(canonical: String, key: String): String = HexFormat.of().formatHex(hmac(canonical, key))

    private fun hmac(canonical: String, key: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
            .doFinal(canonical.toByteArray())
}
