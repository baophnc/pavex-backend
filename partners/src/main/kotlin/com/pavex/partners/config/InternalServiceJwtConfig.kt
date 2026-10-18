package com.pavex.partners.config

import com.pavex.security.IssuerRoutingJwtDecoder
import com.pavex.security.TrustedServiceTokens
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder

@Configuration
class InternalServiceJwtConfig(
    private val trusted: TrustedServiceTokens,
    @Value("\${security.trusted-services.shipment.issuer}")
    private val shipmentIssuer: String,
    @Value("\${security.trusted-services.billing.issuer}")
    private val billingIssuer: String,
) {
    /**
     * Internal endpoints accept tokens from each trusted service, verified with that service's own
     * key. What a caller may do is then limited by the scopes on its token.
     */
    @Bean("internalServiceJwtDecoder")
    fun internalServiceJwtDecoder(): JwtDecoder = IssuerRoutingJwtDecoder(
        mapOf(
            shipmentIssuer to shipmentServiceJwtDecoder(),
            billingIssuer to billingServiceJwtDecoder(),
        ),
    )

    private fun billingServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("billing", "billing-service", PARTNERS_SERVICE)

    @Bean("shipmentServiceJwtDecoder")
    fun shipmentServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("shipment", "shipment-service", PARTNERS_SERVICE)

    companion object {
        private const val PARTNERS_SERVICE = "partners-service"
    }
}
