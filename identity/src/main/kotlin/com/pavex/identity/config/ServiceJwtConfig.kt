package com.pavex.identity.config

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
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

@Configuration
class ServiceJwtConfig(
    @Value("\${security.service-jwt.private-key}")
    private val servicePrivateKeyResource: Resource,
    @Value("\${security.service-jwt.public-key}")
    private val servicePublicKeyResource: Resource,
    @Value("\${security.jwt.public-key}")
    private val authPublicKeyResource: Resource,
    @Value("\${security.jwt.issuer}")
    private val authIssuer: String,
    @Value("\${security.service-jwt.partners.public-key}")
    private val partnersPublicKeyResource: Resource,
    @Value("\${security.service-jwt.partners.issuer}")
    private val partnersIssuer: String,
    @Value("\${security.service-jwt.operations.public-key}")
    private val operationsPublicKeyResource: Resource,
    @Value("\${security.service-jwt.operations.issuer}")
    private val operationsIssuer: String,
) {

    @Bean("serviceJwtEncoder")
    fun serviceJwtEncoder(): JwtEncoder {
        val privateKey = servicePrivateKeyResource.inputStream.use { inputStream ->
            requireNotNull(RsaKeyConverters.pkcs8().convert(inputStream))
        }
        val publicKey = servicePublicKeyResource.inputStream.use { inputStream ->
            requireNotNull(RsaKeyConverters.x509().convert(inputStream))
        }

        return NimbusJwtEncoder
            .withKeyPair(publicKey, privateKey)
            .build()
    }

    @Bean("authServiceJwtDecoder")
    fun authServiceJwtDecoder(): JwtDecoder =
        serviceJwtDecoder(
            publicKeyResource = authPublicKeyResource,
            issuer = authIssuer,
            expectedSubject = AUTH_SERVICE,
            expectedAudience = IDENTITY_SERVICE,
            expectedScope = IDENTITY_INTERNAL_SCOPE,
        )

    @Bean("partnersServiceJwtDecoder")
    fun partnersServiceJwtDecoder(): JwtDecoder =
        serviceJwtDecoder(
            publicKeyResource = partnersPublicKeyResource,
            issuer = partnersIssuer,
            expectedSubject = PARTNERS_SERVICE,
            expectedAudience = IDENTITY_SERVICE,
            expectedScope = IDENTITY_MERCHANT_SCOPE,
        )

    @Bean("operationsServiceJwtDecoder")
    fun operationsServiceJwtDecoder(): JwtDecoder =
        serviceJwtDecoder(
            publicKeyResource = operationsPublicKeyResource,
            issuer = operationsIssuer,
            expectedSubject = OPERATIONS_SERVICE,
            expectedAudience = IDENTITY_SERVICE,
            expectedScope = IDENTITY_WORKFORCE_READ_SCOPE,
        )

    private fun serviceJwtDecoder(
        publicKeyResource: Resource,
        issuer: String,
        expectedSubject: String,
        expectedAudience: String,
        expectedScope: String,
    ): JwtDecoder {
        val publicKey = publicKeyResource.inputStream.use { inputStream ->
            requireNotNull(RsaKeyConverters.x509().convert(inputStream))
        }

        val decoder = NimbusJwtDecoder
            .withPublicKey(publicKey)
            .build()

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                serviceClaimsValidator(
                    expectedSubject = expectedSubject,
                    expectedAudience = expectedAudience,
                    expectedScope = expectedScope,
                ),
            ),
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
                    ),
                )
            }
        }

    companion object {
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val SCOPE_CLAIM = "scope"
        private const val SERVICE_TOKEN_TYPE = "service"
        private const val AUTH_SERVICE = "auth-service"
        private const val PARTNERS_SERVICE = "partners-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val IDENTITY_INTERNAL_SCOPE = "identity.internal"
        private const val IDENTITY_MERCHANT_SCOPE = "identity.merchant"
        private const val IDENTITY_WORKFORCE_READ_SCOPE = "identity.workforce.read"
    }
}
