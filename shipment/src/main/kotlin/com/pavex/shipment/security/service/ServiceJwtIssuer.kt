package com.pavex.shipment.security.service

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
    @Qualifier("shipmentServiceJwtEncoder")
    private val encoder: JwtEncoder,
    @Value("\${security.service-jwt.issuer}")
    private val issuer: String,
    @Value("\${security.service-jwt.ttl:PT1M}")
    private val ttl: Duration,
) {
    fun issueForPricing(now: Instant = Instant.now()): String = issue(
        audience = PRICING_SERVICE,
        scopes = listOf(PRICING_QUOTE_READ_SCOPE),
        now = now,
    )

    fun issueForPartners(now: Instant = Instant.now()): String = issue(
        audience = PARTNERS_SERVICE,
        scopes = listOf(PARTNERS_ORDER_SCOPE, PARTNERS_LOCATION_SCOPE, PARTNERS_PICKUP_ADDRESS_SCOPE),
        now = now,
    )

    fun issueForNetwork(now: Instant = Instant.now()): String = issue(
        audience = NETWORK_SERVICE,
        scopes = listOf(NETWORK_SHIPMENT_RESERVE_SCOPE),
        now = now,
    )

    fun issueForOperations(now: Instant = Instant.now()): String = issue(
        audience = OPERATIONS_SERVICE,
        scopes = listOf(OPERATIONS_SHIPMENT_SCOPE),
        now = now,
    )

    private fun issue(audience: String, scopes: List<String>, now: Instant): String {
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(SHIPMENT_SERVICE)
            .audience(listOf(audience))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(PavexJwtClaims.TOKEN_TYPE, PavexTokenTypes.SERVICE)
            .claim(PavexJwtClaims.SCOPE, scopes.joinToString(" "))
            .build()
        return encoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims,
            ),
        ).tokenValue
    }

    companion object {
        private const val SHIPMENT_SERVICE = "shipment-service"
        private const val PRICING_SERVICE = "pricing-service"
        private const val PARTNERS_SERVICE = "partners-service"
        private const val NETWORK_SERVICE = "network-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val PRICING_QUOTE_READ_SCOPE = "pricing.quote.read"
        private const val PARTNERS_ORDER_SCOPE = "partners.orders.evaluate"
        private const val PARTNERS_LOCATION_SCOPE = "partners.locations.read"
        private const val PARTNERS_PICKUP_ADDRESS_SCOPE = "partners.pickup-addresses.read"
        private const val NETWORK_SHIPMENT_RESERVE_SCOPE = "network.shipments.reserve"
        private const val OPERATIONS_SHIPMENT_SCOPE = "operations.shipments.execute"
    }
}
