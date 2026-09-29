package com.pavex.billing.config

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
class JwtConfig(
    @Value("\${security.jwt.public-key}")
    private val publicKeyResource: Resource,
    @Value("\${security.jwt.issuer}")
    private val issuer: String,
) {
    @Bean("userJwtDecoder")
    fun userJwtDecoder(): JwtDecoder {
        val publicKey = publicKeyResource.inputStream.use { inputStream ->
            requireNotNull(RsaKeyConverters.x509().convert(inputStream))
        }
        val decoder = NimbusJwtDecoder.withPublicKey(publicKey).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                tokenTypeValidator("user"),
            ),
        )
        return decoder
    }

    private fun tokenTypeValidator(expectedType: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            if (jwt.getClaimAsString("typ") == expectedType) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_token", "JWT token type is invalid", null),
                )
            }
        }
}
