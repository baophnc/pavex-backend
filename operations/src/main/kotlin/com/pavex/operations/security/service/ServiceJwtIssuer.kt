package com.pavex.operations.security.service

import com.pavex.common.security.PavexJwtClaims
import com.pavex.common.security.PavexTokenTypes
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
    @Qualifier("operationsServiceJwtEncoder")
    private val encoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    private val issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    private val ttl: Duration,
) {
    fun issueForIdentity(now: Instant = Instant.now()): String =
        issue(IDENTITY_SERVICE, "$IDENTITY_WORKFORCE_READ_SCOPE $IDENTITY_WORKFORCE_PROVISION_SCOPE", now)

    fun issueForNetwork(now: Instant = Instant.now()): String =
        issue(NETWORK_SERVICE, NETWORK_OPERATIONS_READ_SCOPE, now)

    private fun issue(audience: String, scope: String, now: Instant): String {
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(OPERATIONS_SERVICE)
            .audience(listOf(audience))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(PavexJwtClaims.TOKEN_TYPE, PavexTokenTypes.SERVICE)
            .claim(PavexJwtClaims.SCOPE, scope)
            .build()
        return encoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims,
            ),
        ).tokenValue
    }

    companion object {
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val NETWORK_SERVICE = "network-service"
        private const val IDENTITY_WORKFORCE_READ_SCOPE = "identity.workforce.read"
        private const val IDENTITY_WORKFORCE_PROVISION_SCOPE = "identity.workforce.provision"
        private const val NETWORK_OPERATIONS_READ_SCOPE = "network.operations.read"
    }
}
