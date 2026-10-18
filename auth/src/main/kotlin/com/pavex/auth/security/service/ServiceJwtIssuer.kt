package com.pavex.auth.security.service

import com.pavex.security.ServiceTokenIssuer
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/** Auth signs its service tokens with the same key as its user tokens (`security.jwt.*`). */
@Service
class ServiceJwtIssuer(
    encoder: JwtEncoder,
    @Value("\${security.jwt.issuer}")
    issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    ttl: Duration,
) {
    private val tokens = ServiceTokenIssuer(encoder, issuer, AUTH_SERVICE, ttl)

    fun issueForIdentity(
        now: Instant = Instant.now(),
    ): String = tokens.issue(IDENTITY_SERVICE, IDENTITY_INTERNAL_SCOPE, now)

    companion object {
        private const val AUTH_SERVICE = "auth-service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val IDENTITY_INTERNAL_SCOPE = "identity.internal"
    }
}
