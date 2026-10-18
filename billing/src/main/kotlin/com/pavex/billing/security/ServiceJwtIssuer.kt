package com.pavex.billing.security

import com.pavex.security.ServiceTokenIssuer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/** Short-lived tokens billing presents to Partners and Operations internal APIs. */
@Service
class ServiceJwtIssuer(
    @Qualifier("serviceJwtEncoder")
    encoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    ttl: Duration,
) {
    private val tokens = ServiceTokenIssuer(encoder, issuer, BILLING_SERVICE, ttl)

    fun issueForPartners(now: Instant = Instant.now()): String =
        tokens.issue(PARTNERS_SERVICE, listOf(PARTNERS_MERCHANT_READ_SCOPE), now)

    fun issueForOperations(now: Instant = Instant.now()): String =
        tokens.issue(OPERATIONS_SERVICE, listOf(OPERATIONS_ACCESS_CHECK_SCOPE), now)

    companion object {
        const val BILLING_SERVICE = "billing-service"
        private const val PARTNERS_SERVICE = "partners-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val PARTNERS_MERCHANT_READ_SCOPE = "partners.merchants.read"
        private const val OPERATIONS_ACCESS_CHECK_SCOPE = "operations.access.check"
    }
}
