package com.pavex.billing.security

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

/** Short-lived tokens billing presents to Partners and Operations internal APIs. */
@Service
class ServiceJwtIssuer(
    @Qualifier("billingServiceJwtEncoder")
    private val encoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    private val issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    private val ttl: Duration,
) {
    fun issueForPartners(now: Instant = Instant.now()): String =
        issue(PARTNERS_SERVICE, listOf(PARTNERS_MERCHANT_READ_SCOPE), now)

    fun issueForOperations(now: Instant = Instant.now()): String =
        issue(OPERATIONS_SERVICE, listOf(OPERATIONS_ACCESS_CHECK_SCOPE), now)

    private fun issue(audience: String, scopes: List<String>, now: Instant): String {
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(BILLING_SERVICE)
            .audience(listOf(audience))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(PavexJwtClaims.TOKEN_TYPE, PavexTokenTypes.SERVICE)
            .claim(PavexJwtClaims.SCOPE, scopes.joinToString(" "))
            .build()
        return encoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims),
        ).tokenValue
    }

    companion object {
        const val BILLING_SERVICE = "billing-service"
        private const val PARTNERS_SERVICE = "partners-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val PARTNERS_MERCHANT_READ_SCOPE = "partners.merchants.read"
        private const val OPERATIONS_ACCESS_CHECK_SCOPE = "operations.access.check"
    }
}
