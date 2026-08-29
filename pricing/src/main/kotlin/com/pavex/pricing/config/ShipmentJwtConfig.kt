package com.pavex.pricing.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
@ConditionalOnProperty(prefix = "pricing.shipment", name = ["integration-enabled"], havingValue = "true")
class ShipmentJwtConfig(
    @Value("\${security.trusted-services.shipment.public-key}")
    private val shipmentPublicKey: Resource,
    @Value("\${security.trusted-services.shipment.issuer}")
    private val shipmentIssuer: String,
) {
    @Bean("shipmentServiceJwtDecoder")
    fun shipmentServiceJwtDecoder(): JwtDecoder {
        val publicKey = shipmentPublicKey.inputStream.use {
            requireNotNull(RsaKeyConverters.x509().convert(it))
        }
        val decoder = NimbusJwtDecoder.withPublicKey(publicKey).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(shipmentIssuer),
                exactClaim("sub", SHIPMENT_SERVICE),
                exactClaim("typ", SERVICE_TOKEN_TYPE),
                audience(PRICING_SERVICE),
                scope(PRICING_QUOTE_READ_SCOPE),
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
        private const val SHIPMENT_SERVICE = "shipment-service"
        private const val PRICING_SERVICE = "pricing-service"
        private const val PRICING_QUOTE_READ_SCOPE = "pricing.quote.read"
    }
}
