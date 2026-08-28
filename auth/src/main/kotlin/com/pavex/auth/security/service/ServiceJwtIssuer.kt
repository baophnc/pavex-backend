package com.pavex.auth.security.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ServiceJwtIssuer(
    private val jwtEncoder: JwtEncoder,
    @Value("\${security.jwt.issuer}")
    private val issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    private val ttl: Duration,
) {

    fun issueForIdentity(
        now: Instant = Instant.now(),
    ): String =
        issue(
            subject = AUTH_SERVICE,
            audience = IDENTITY_SERVICE,
            scope = IDENTITY_INTERNAL_SCOPE,
            now = now,
        )

    private fun issue(
        subject: String,
        audience: String,
        scope: String,
        now: Instant,
    ): String {
        val claims = JwtClaimsSet
            .builder()
            .issuer(issuer)
            .subject(subject)
            .audience(listOf(audience))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(TOKEN_TYPE_CLAIM, SERVICE_TOKEN_TYPE)
            .claim(SCOPE_CLAIM, scope)
            .build()

        val header = JwsHeader
            .with(SignatureAlgorithm.RS256)
            .build()

        return jwtEncoder
            .encode(JwtEncoderParameters.from(header, claims))
            .tokenValue
    }

    companion object {
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val SCOPE_CLAIM = "scope"
        private const val SERVICE_TOKEN_TYPE = "service"
        private const val AUTH_SERVICE = "auth-service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val IDENTITY_INTERNAL_SCOPE = "identity.internal"
    }
}
