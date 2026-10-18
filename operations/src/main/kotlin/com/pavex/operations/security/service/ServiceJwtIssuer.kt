package com.pavex.operations.security.service

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
    private val tokens = ServiceTokenIssuer(encoder, issuer, OPERATIONS_SERVICE, ttl)

    fun issueForIdentity(now: Instant = Instant.now()): String =
        tokens.issue(IDENTITY_SERVICE, listOf(IDENTITY_WORKFORCE_READ_SCOPE, IDENTITY_WORKFORCE_PROVISION_SCOPE), now)

    fun issueForNetwork(now: Instant = Instant.now()): String =
        tokens.issue(NETWORK_SERVICE, NETWORK_OPERATIONS_READ_SCOPE, now)

    companion object {
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val NETWORK_SERVICE = "network-service"
        private const val IDENTITY_WORKFORCE_READ_SCOPE = "identity.workforce.read"
        private const val IDENTITY_WORKFORCE_PROVISION_SCOPE = "identity.workforce.provision"
        private const val NETWORK_OPERATIONS_READ_SCOPE = "network.operations.read"
    }
}
