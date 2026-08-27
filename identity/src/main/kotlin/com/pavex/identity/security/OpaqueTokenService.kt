package com.pavex.identity.security

import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Service
class OpaqueTokenService {

    private val secureRandom =
        SecureRandom()

    fun generate(): String {

        val bytes =
            ByteArray(32)

        secureRandom.nextBytes(
            bytes
        )

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    fun hash(
        rawToken: String,
    ): String {

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    rawToken.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

        return digest.joinToString("") {
            "%02x".format(
                it.toInt() and 0xff
            )
        }
    }
}
