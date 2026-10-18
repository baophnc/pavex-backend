package com.pavex.network.config

import com.pavex.security.TrustedServiceTokens
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder

/** The services that may call Network's internal API, and the one scope each must carry. */
@Configuration
class InternalServiceJwtConfig(
    private val trusted: TrustedServiceTokens,
) {
    @Bean("pricingServiceJwtDecoder")
    fun pricingServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("pricing", PRICING_SERVICE, NETWORK_SERVICE, NETWORK_PRICING_RESOLVE_SCOPE)

    @Bean("shipmentServiceJwtDecoder")
    fun shipmentServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("shipment", SHIPMENT_SERVICE, NETWORK_SERVICE, NETWORK_SHIPMENT_RESERVE_SCOPE)

    @Bean("operationsServiceJwtDecoder")
    fun operationsServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("operations", OPERATIONS_SERVICE, NETWORK_SERVICE, NETWORK_OPERATIONS_READ_SCOPE)

    /** Fleet only reads the lane, hubs and planned times of line-haul route legs. */
    @Bean("fleetServiceJwtDecoder")
    fun fleetServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("fleet", FLEET_SERVICE, NETWORK_SERVICE, NETWORK_FLEET_READ_SCOPE)

    /** Partners only asks which hub serves a merchant's warehouse ward. */
    @Bean("partnersServiceJwtDecoder")
    fun partnersServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("partners", PARTNERS_SERVICE, NETWORK_SERVICE, NETWORK_PARTNERS_RESOLVE_SCOPE)

    companion object {
        private const val PRICING_SERVICE = "pricing-service"
        private const val SHIPMENT_SERVICE = "shipment-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val NETWORK_SERVICE = "network-service"
        private const val NETWORK_PRICING_RESOLVE_SCOPE = "network.pricing.resolve"
        private const val NETWORK_SHIPMENT_RESERVE_SCOPE = "network.shipments.reserve"
        private const val NETWORK_OPERATIONS_READ_SCOPE = "network.operations.read"
        private const val FLEET_SERVICE = "fleet-service"
        private const val NETWORK_FLEET_READ_SCOPE = "network.fleet.read"
        private const val PARTNERS_SERVICE = "partners-service"
        private const val NETWORK_PARTNERS_RESOLVE_SCOPE = "network.partners.resolve"
    }
}
