package com.pavex.identity.security.service

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
    private val tokens = ServiceTokenIssuer(encoder, issuer, IDENTITY_SERVICE, ttl)

    fun issueForAuth(
        now: Instant = Instant.now(),
    ): String = tokens.issue(AUTH_SERVICE, AUTH_INTERNAL_SCOPE, now)

    companion object {
        private const val IDENTITY_SERVICE = "identity-service"
        private const val AUTH_SERVICE = "auth-service"
        private const val AUTH_INTERNAL_SCOPE = "auth.internal"
    }
}
