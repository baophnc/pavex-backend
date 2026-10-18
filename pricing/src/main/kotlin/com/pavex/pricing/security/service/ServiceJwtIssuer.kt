package com.pavex.pricing.security.service

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
    private val tokens = ServiceTokenIssuer(encoder, issuer, PRICING_SERVICE, ttl)

    fun issueForNetwork(now: Instant = Instant.now()): String =
        tokens.issue(NETWORK_SERVICE, NETWORK_PRICING_RESOLVE_SCOPE, now)

    companion object {
        private const val PRICING_SERVICE = "pricing-service"
        private const val NETWORK_SERVICE = "network-service"
        private const val NETWORK_PRICING_RESOLVE_SCOPE = "network.pricing.resolve"
    }
}
