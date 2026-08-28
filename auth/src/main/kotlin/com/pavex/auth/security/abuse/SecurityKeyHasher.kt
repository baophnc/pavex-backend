package com.pavex.auth.security.abuse

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class SecurityKeyHasher(
    @Value("\${auth.security.key-hash-secret:}")
    secretInput: String,
) {

    private val secret: ByteArray =
        runCatching {
            Base64.getDecoder()
                .decode(secretInput.trim())
        }.getOrElse {
            throw IllegalStateException(
                "auth.security.key-hash-secret must be Base64 encoded",
            )
        }.also { bytes ->
            require(bytes.size >= 32) {
                "auth.security.key-hash-secret must decode to at least 32 bytes"
            }
        }

    fun hash(
        value: String,
    ): String {
        val mac = Mac.getInstance(HMAC_SHA_256)
        mac.init(
            SecretKeySpec(
                secret,
                HMAC_SHA_256,
            )
        )

        return mac
            .doFinal(
                value
                    .trim()
                    .toByteArray(
                        StandardCharsets.UTF_8
                    )
            )
            .joinToString("") { byte ->
                "%02x".format(byte)
            }
    }

    companion object {
        private const val HMAC_SHA_256 = "HmacSHA256"
    }
}
