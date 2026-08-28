package com.pavex.auth.security

import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Service
class OpaqueTokenService {

    private val secureRandom =
        SecureRandom()

    data class IssuedToken(
        val raw: String,
        val hash: String,
    )

    fun issue(): IssuedToken {

        val bytes =
            ByteArray(32)

        secureRandom.nextBytes(
            bytes
        )

        val raw =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes)

        return IssuedToken(
            raw = raw,
            hash = hash(raw),
        )
    }


    fun generateRawToken(): String =
        issue().raw


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


    fun hashToken(
        rawToken: String,
    ): String =
        hash(rawToken)
}
