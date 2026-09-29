package com.pavex.partners.config

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
    @Value("\${security.trusted-services.shipment.public-key}")
    private val shipmentPublicKey: Resource,
    @Value("\${security.trusted-services.shipment.issuer}")
    private val shipmentIssuer: String,
    @Value("\${security.trusted-services.billing.public-key}")
    private val billingPublicKey: Resource,
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

    private fun billingServiceJwtDecoder(): JwtDecoder {
        val publicKey = billingPublicKey.inputStream.use {
            requireNotNull(RsaKeyConverters.x509().convert(it))
        }
        val decoder = NimbusJwtDecoder.withPublicKey(publicKey).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(billingIssuer),
                exactClaim("sub", "billing-service"),
                exactClaim("typ", "service"),
                audience("partners-service"),
            ),
        )
        return decoder
    }

    @Bean("shipmentServiceJwtDecoder")
    fun shipmentServiceJwtDecoder(): JwtDecoder {
        val publicKey = shipmentPublicKey.inputStream.use {
            requireNotNull(RsaKeyConverters.x509().convert(it))
        }
        val decoder = NimbusJwtDecoder.withPublicKey(publicKey).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(shipmentIssuer),
                exactClaim("sub", "shipment-service"),
                exactClaim("typ", "service"),
                audience("partners-service"),
            ),
        )
        return decoder
    }

    private fun exactClaim(name: String, expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt -> result(jwt.getClaimAsString(name) == expected, "Invalid $name claim") }

    private fun audience(expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt -> result(expected in jwt.audience.orEmpty(), "Invalid audience") }

    private fun result(valid: Boolean, description: String): OAuth2TokenValidatorResult =
        if (valid) OAuth2TokenValidatorResult.success()
        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", description, null))
}
