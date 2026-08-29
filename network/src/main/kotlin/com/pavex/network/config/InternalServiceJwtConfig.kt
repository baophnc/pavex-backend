package com.pavex.network.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.security.converter.RsaKeyConverters
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
class InternalServiceJwtConfig(
    @Value("\${security.trusted-services.pricing.public-key}")
    private val pricingPublicKey: Resource,
    @Value("\${security.trusted-services.pricing.issuer}")
    private val pricingIssuer: String,
    @Value("\${security.trusted-services.shipment.public-key}")
    private val shipmentPublicKey: Resource,
    @Value("\${security.trusted-services.shipment.issuer}")
    private val shipmentIssuer: String,
) {
    @Bean("pricingServiceJwtDecoder")
    fun pricingServiceJwtDecoder(): JwtDecoder = decoder(
        publicKeyResource = pricingPublicKey,
        issuer = pricingIssuer,
        subject = PRICING_SERVICE,
        requiredScope = NETWORK_PRICING_RESOLVE_SCOPE,
    )

    @Bean("shipmentServiceJwtDecoder")
    fun shipmentServiceJwtDecoder(): JwtDecoder = decoder(
        publicKeyResource = shipmentPublicKey,
        issuer = shipmentIssuer,
        subject = SHIPMENT_SERVICE,
        requiredScope = NETWORK_SHIPMENT_RESERVE_SCOPE,
    )

    private fun decoder(
        publicKeyResource: Resource,
        issuer: String,
        subject: String,
        requiredScope: String,
    ): JwtDecoder {
        val publicKey = publicKeyResource.inputStream.use {
            requireNotNull(RsaKeyConverters.x509().convert(it))
        }
        val decoder = NimbusJwtDecoder.withPublicKey(publicKey).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                exactClaim("sub", subject),
                exactClaim("typ", SERVICE_TOKEN_TYPE),
                audience(NETWORK_SERVICE),
                scope(requiredScope),
            ),
        )
        return decoder
    }

    private fun exactClaim(name: String, expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt -> result(jwt.getClaimAsString(name) == expected, "Invalid $name claim") }

    private fun audience(expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt -> result(expected in jwt.audience.orEmpty(), "Invalid audience") }

    private fun scope(expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            val scopes = jwt.getClaimAsString("scope")?.split(' ').orEmpty()
            result(expected in scopes, "Invalid scope")
        }

    private fun result(valid: Boolean, description: String): OAuth2TokenValidatorResult =
        if (valid) OAuth2TokenValidatorResult.success()
        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", description, null))

    companion object {
        private const val SERVICE_TOKEN_TYPE = "service"
        private const val PRICING_SERVICE = "pricing-service"
        private const val SHIPMENT_SERVICE = "shipment-service"
        private const val NETWORK_SERVICE = "network-service"
        private const val NETWORK_PRICING_RESOLVE_SCOPE = "network.pricing.resolve"
        private const val NETWORK_SHIPMENT_RESERVE_SCOPE = "network.shipments.reserve"
    }
}
