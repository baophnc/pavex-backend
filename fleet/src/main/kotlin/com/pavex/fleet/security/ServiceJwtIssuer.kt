package com.pavex.fleet.security

import com.pavex.security.ServiceTokenIssuer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/** Short-lived tokens Fleet presents to the Operations and Network internal APIs. */
@Service
class ServiceJwtIssuer(
    @Qualifier("serviceJwtEncoder")
    encoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    ttl: Duration,
) {
    private val tokens = ServiceTokenIssuer(encoder, issuer, FLEET_SERVICE, ttl)

    fun issueForOperations(now: Instant = Instant.now()): String =
        tokens.issue(OPERATIONS_SERVICE, OPERATIONS_FLEET_READ_SCOPE, now)

    fun issueForNetwork(now: Instant = Instant.now()): String =
        tokens.issue(NETWORK_SERVICE, NETWORK_FLEET_READ_SCOPE, now)

    companion object {
        const val FLEET_SERVICE = "fleet-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val NETWORK_SERVICE = "network-service"
        private const val OPERATIONS_FLEET_READ_SCOPE = "operations.fleet.read"
        private const val NETWORK_FLEET_READ_SCOPE = "network.fleet.read"
    }
}
