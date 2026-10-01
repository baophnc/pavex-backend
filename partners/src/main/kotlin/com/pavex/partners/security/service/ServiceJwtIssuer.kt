package com.pavex.partners.security.service

import org.springframework.beans.factory.annotation.Qualifier
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
    @Qualifier("serviceJwtEncoder")
    private val jwtEncoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    private val issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    private val ttl: Duration,
) {

    fun issueForIdentity(
        now: Instant = Instant.now(),
    ): String = issue(IDENTITY_SERVICE, IDENTITY_MERCHANT_SCOPE, now)

    /** Asks Network which hub serves a ward. */
    fun issueForNetwork(): String = issue("network-service", "network.partners.resolve")

    /** Asks Operations which hubs a user works at. */
    fun issueForOperations(): String = issue("operations-service", "operations.hub-scope.read")

    private fun issue(audience: String, scope: String, now: Instant = Instant.now()): String {
        val claims = JwtClaimsSet
            .builder()
            .issuer(issuer)
            .subject(PARTNERS_SERVICE)
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
        private const val PARTNERS_SERVICE = "partners-service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val IDENTITY_MERCHANT_SCOPE = "identity.merchant"
    }
}
