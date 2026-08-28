package com.pavex.auth.config

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
class ServiceJwtConfig(
    @Value("\${security.service-jwt.identity.issuer}")
    private val identityIssuer: String,
    @Value("\${security.service-jwt.identity.public-key}")
    private val identityPublicKeyResource: Resource,
) {

    @Bean("identityServiceJwtDecoder")
    fun identityServiceJwtDecoder(): JwtDecoder {
        val publicKey = identityPublicKeyResource.inputStream.use { inputStream ->
            requireNotNull(RsaKeyConverters.x509().convert(inputStream))
        }

        val decoder = NimbusJwtDecoder
            .withPublicKey(publicKey)
            .build()

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(identityIssuer),
                serviceClaimsValidator(
                    expectedSubject = IDENTITY_SERVICE,
                    expectedAudience = AUTH_SERVICE,
                    expectedScope = AUTH_INTERNAL_SCOPE,
                ),
            )
        )

        return decoder
    }

    private fun serviceClaimsValidator(
        expectedSubject: String,
        expectedAudience: String,
        expectedScope: String,
    ): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            val scopes = jwt.getClaimAsString(SCOPE_CLAIM)
                .orEmpty()
                .split(' ')
                .filter { it.isNotBlank() }
                .toSet()

            val valid =
                jwt.getClaimAsString(TOKEN_TYPE_CLAIM) == SERVICE_TOKEN_TYPE &&
                    jwt.subject == expectedSubject &&
                    expectedAudience in jwt.audience.orEmpty() &&
                    expectedScope in scopes

            if (valid) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error(
                        "invalid_token",
                        "Service JWT claims are invalid",
                        null,
                    )
                )
            }
        }

    companion object {
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val SCOPE_CLAIM = "scope"
        private const val SERVICE_TOKEN_TYPE = "service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val AUTH_SERVICE = "auth-service"
        private const val AUTH_INTERNAL_SCOPE = "auth.internal"
    }
}
