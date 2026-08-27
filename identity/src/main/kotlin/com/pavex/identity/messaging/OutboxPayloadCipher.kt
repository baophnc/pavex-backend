package com.pavex.identity.messaging

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class OutboxPayloadCipher(

    @Value("\${identity.outbox.payload-encryption-key:}")
    encryptionKeyBase64: String,
) {

    private val secureRandom = SecureRandom()

    private val key =
        decodeKey(
            encryptionKeyBase64
        )

    fun encrypt(
        plaintext: String,
        eventType: String,
        eventVersion: Int,
    ): String {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(
                TAG_LENGTH_BITS,
                iv,
            ),
        )

        cipher.updateAAD(
            associatedData(
                eventType,
                eventVersion,
            )
        )

        val encrypted =
            cipher.doFinal(
                plaintext.toByteArray(
                    StandardCharsets.UTF_8
                )
            )

        val combined =
            ByteArray(
                iv.size + encrypted.size
            )

        System.arraycopy(
            iv,
            0,
            combined,
            0,
            iv.size,
        )

        System.arraycopy(
            encrypted,
            0,
            combined,
            iv.size,
            encrypted.size,
        )

        return "$FORMAT_VERSION.${Base64.getUrlEncoder().withoutPadding().encodeToString(combined)}"
    }

    fun decrypt(
        ciphertext: String,
        eventType: String,
        eventVersion: Int,
    ): String {
        val parts =
            ciphertext.split(
                '.',
                limit = 2,
            )

        require(
            parts.size == 2 &&
                parts[0] == FORMAT_VERSION
        ) {
            "Unsupported outbox payload format"
        }

        val combined =
            try {
                Base64
                    .getUrlDecoder()
                    .decode(parts[1])
            } catch (exception: IllegalArgumentException) {
                throw IllegalStateException(
                    "Invalid encrypted outbox payload",
                    exception,
                )
            }

        require(
            combined.size > IV_LENGTH_BYTES
        ) {
            "Invalid encrypted outbox payload"
        }

        val iv =
            combined.copyOfRange(
                0,
                IV_LENGTH_BYTES,
            )

        val encrypted =
            combined.copyOfRange(
                IV_LENGTH_BYTES,
                combined.size,
            )

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(
                TAG_LENGTH_BITS,
                iv,
            ),
        )

        cipher.updateAAD(
            associatedData(
                eventType,
                eventVersion,
            )
        )

        return String(
            cipher.doFinal(encrypted),
            StandardCharsets.UTF_8,
        )
    }

    private fun associatedData(
        eventType: String,
        eventVersion: Int,
    ): ByteArray =
        "$eventType:$eventVersion"
            .toByteArray(
                StandardCharsets.UTF_8
            )

    private fun decodeKey(
        value: String,
    ): SecretKeySpec {
        require(value.isNotBlank()) {
            "identity.outbox.payload-encryption-key must be configured"
        }

        val decoded =
            try {
                Base64
                    .getDecoder()
                    .decode(value.trim())
            } catch (exception: IllegalArgumentException) {
                throw IllegalStateException(
                    "identity.outbox.payload-encryption-key must be Base64 encoded",
                    exception,
                )
            }

        require(decoded.size == KEY_LENGTH_BYTES) {
            "identity.outbox.payload-encryption-key must decode to 32 bytes"
        }

        return SecretKeySpec(
            decoded,
            "AES",
        )
    }

    companion object {
        private const val FORMAT_VERSION = "v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_LENGTH_BYTES = 32
    }
}
