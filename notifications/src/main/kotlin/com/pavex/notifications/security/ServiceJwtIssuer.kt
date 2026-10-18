package com.pavex.notifications.security

import com.pavex.security.ServiceTokenIssuer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/** Short-lived tokens Notifications presents to Operations to learn who works where. */
@Service
class ServiceJwtIssuer(
    @Qualifier("serviceJwtEncoder")
    encoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    ttl: Duration,
) {
    private val tokens = ServiceTokenIssuer(encoder, issuer, NOTIFICATIONS_SERVICE, ttl)

    fun issueForOperations(now: Instant = Instant.now()): String =
        tokens.issue(OPERATIONS_SERVICE, OPERATIONS_PEOPLE_READ, now)

    companion object {
        const val NOTIFICATIONS_SERVICE = "notifications-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val OPERATIONS_PEOPLE_READ = "operations.people.read"
    }
}
