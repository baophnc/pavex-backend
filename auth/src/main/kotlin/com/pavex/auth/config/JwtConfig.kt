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
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Configuration
class JwtConfig(

    @Value("\${security.jwt.private-key}")
    private val privateKeyResource: Resource,

    @Value("\${security.jwt.public-key}")
    private val publicKeyResource: Resource,

    @Value("\${security.jwt.issuer}")
    private val issuer: String,
) {

    @Bean
    fun rsaPrivateKey(): RSAPrivateKey =
        privateKeyResource.inputStream.use { inputStream ->
            requireNotNull(RsaKeyConverters.pkcs8().convert(inputStream))
        }

    @Bean
    fun rsaPublicKey(): RSAPublicKey =
        publicKeyResource.inputStream.use { inputStream ->
            requireNotNull(RsaKeyConverters.x509().convert(inputStream))
        }

    @Bean
    fun jwtEncoder(
        publicKey: RSAPublicKey,
        privateKey: RSAPrivateKey,
    ): JwtEncoder =
        NimbusJwtEncoder
            .withKeyPair(publicKey, privateKey)
            .build()

    @Bean("userJwtDecoder")
    fun userJwtDecoder(
        publicKey: RSAPublicKey,
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder
            .withPublicKey(publicKey)
            .build()

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                tokenTypeValidator(USER_TOKEN_TYPE),
            )
        )

        return decoder
    }

    private fun tokenTypeValidator(
        expectedType: String,
    ): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            if (jwt.getClaimAsString(TOKEN_TYPE_CLAIM) == expectedType) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error(
                        "invalid_token",
                        "JWT token type is invalid",
                        null,
                    )
                )
            }
        }

    companion object {
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val USER_TOKEN_TYPE = "user"
    }
}
