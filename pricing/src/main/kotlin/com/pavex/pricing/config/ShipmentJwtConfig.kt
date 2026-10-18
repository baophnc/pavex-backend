package com.pavex.pricing.config

import com.pavex.security.TrustedServiceTokens
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder

/** Shipment is the only caller of Pricing's internal API, for quotes. */
@Configuration
@ConditionalOnProperty(prefix = "pricing.shipment", name = ["integration-enabled"], havingValue = "true")
class ShipmentJwtConfig(
    private val trusted: TrustedServiceTokens,
) {
    @Bean("shipmentServiceJwtDecoder")
    fun shipmentServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("shipment", SHIPMENT_SERVICE, PRICING_SERVICE, PRICING_QUOTE_READ_SCOPE)

    companion object {
        private const val SHIPMENT_SERVICE = "shipment-service"
        private const val PRICING_SERVICE = "pricing-service"
        private const val PRICING_QUOTE_READ_SCOPE = "pricing.quote.read"
    }
}
