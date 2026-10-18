package com.pavex.shipment.security.service

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
    private val tokens = ServiceTokenIssuer(encoder, issuer, SHIPMENT_SERVICE, ttl)

    fun issueForPricing(now: Instant = Instant.now()): String =
        tokens.issue(PRICING_SERVICE, listOf(PRICING_QUOTE_READ_SCOPE), now)

    fun issueForPartners(now: Instant = Instant.now()): String =
        tokens.issue(PARTNERS_SERVICE, listOf(PARTNERS_ORDER_SCOPE, PARTNERS_LOCATION_SCOPE, PARTNERS_PICKUP_ADDRESS_SCOPE), now)

    fun issueForNetwork(now: Instant = Instant.now()): String =
        tokens.issue(NETWORK_SERVICE, listOf(NETWORK_SHIPMENT_RESERVE_SCOPE), now)

    fun issueForOperations(now: Instant = Instant.now()): String =
        tokens.issue(OPERATIONS_SERVICE, listOf(OPERATIONS_SHIPMENT_SCOPE), now)

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
