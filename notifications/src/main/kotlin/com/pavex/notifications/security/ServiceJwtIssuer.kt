package com.pavex.notifications.security

import com.pavex.common.security.PavexJwtClaims
import com.pavex.common.security.PavexTokenTypes
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Short-lived tokens Notifications presents to Operations to learn who works where. */
@Service
class ServiceJwtIssuer(
    @Qualifier("notificationsServiceJwtEncoder")
    private val encoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    private val issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    private val ttl: Duration,
) {
    fun issueForOperations(now: Instant = Instant.now()): String {
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(NOTIFICATIONS_SERVICE)
            .audience(listOf(OPERATIONS_SERVICE))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(PavexJwtClaims.TOKEN_TYPE, PavexTokenTypes.SERVICE)
            .claim(PavexJwtClaims.SCOPE, OPERATIONS_PEOPLE_READ)
            .build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).tokenValue
    }

    companion object {
        const val NOTIFICATIONS_SERVICE = "notifications-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val OPERATIONS_PEOPLE_READ = "operations.people.read"
    }
}
