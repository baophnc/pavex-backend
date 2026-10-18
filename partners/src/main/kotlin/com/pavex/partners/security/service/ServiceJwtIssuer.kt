package com.pavex.partners.security.service

import com.pavex.security.ServiceTokenIssuer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class ServiceJwtIssuer(
    @Qualifier("serviceJwtEncoder")
    encoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    ttl: Duration,
) {
    private val tokens = ServiceTokenIssuer(encoder, issuer, PARTNERS_SERVICE, ttl)

    fun issueForIdentity(
        now: Instant = Instant.now(),
    ): String = tokens.issue(IDENTITY_SERVICE, IDENTITY_MERCHANT_SCOPE, now)

    /** Asks Network which hub serves a ward. */
    fun issueForNetwork(): String = tokens.issue("network-service", "network.partners.resolve")

    /** Asks Operations which hubs a user works at. */
    fun issueForOperations(): String = tokens.issue("operations-service", "operations.hub-scope.read")

    companion object {
        private const val PARTNERS_SERVICE = "partners-service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val IDENTITY_MERCHANT_SCOPE = "identity.merchant"
    }
}
