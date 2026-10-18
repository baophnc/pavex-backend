package com.pavex.security

import com.pavex.common.security.PavexJwtClaims
import com.pavex.common.security.PavexTokenTypes
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Signs the short-lived RS256 tokens [subject] presents to another service's `/internal` API:
 * `typ=service`, one audience, a fresh `jti` and the scopes as one space-separated `scope` claim.
 */
class ServiceTokenIssuer(
    private val encoder: JwtEncoder,
    private val issuer: String,
    private val subject: String,
    private val ttl: Duration,
) {
    fun issue(audience: String, scopes: List<String>, now: Instant = Instant.now()): String {
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject)
            .audience(listOf(audience))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(PavexJwtClaims.TOKEN_TYPE, PavexTokenTypes.SERVICE)
            .claim(PavexJwtClaims.SCOPE, scopes.joinToString(" "))
            .build()
        return encoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims),
        ).tokenValue
    }

    fun issue(audience: String, scope: String, now: Instant = Instant.now()): String = issue(audience, listOf(scope), now)
}
