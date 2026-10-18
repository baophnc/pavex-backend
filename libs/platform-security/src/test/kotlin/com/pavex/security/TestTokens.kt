package com.pavex.security

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64

/** A throwaway RSA key pair, as PEM files too, and a signer for hand-made tokens. */
class TestKeyPair {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val publicKey = pair.public as RSAPublicKey
    val privateKey = pair.private as RSAPrivateKey
    val encoder: JwtEncoder = RsaKeys.encoder(publicKey, privateKey)

    fun sign(claims: Map<String, Any>, now: Instant = Instant.now()): String {
        val builder = JwtClaimsSet.builder().issuedAt(now).expiresAt(now.plusSeconds(60))
        claims.forEach { (name, value) -> builder.claim(name, value) }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), builder.build())).tokenValue
    }

    fun writePem(directory: Path): Pair<Path, Path> {
        val public = directory.resolve("public.pem")
        val private = directory.resolve("private.pem")
        Files.writeString(public, pem("PUBLIC KEY", publicKey.encoded))
        Files.writeString(private, pem("PRIVATE KEY", privateKey.encoded))
        return public to private
    }

    private fun pem(type: String, der: ByteArray): String =
        "-----BEGIN $type-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END $type-----\n"
}
