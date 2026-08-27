package com.pavex.identity.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.security.converter.RsaKeyConverters
import org.springframework.security.oauth2.core.OAuth2TokenValidator
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
    fun rsaPrivateKey(): RSAPrivateKey {
        return privateKeyResource.inputStream.use { inputStream ->
            requireNotNull(
                RsaKeyConverters.pkcs8().convert(inputStream)
            )
        }
    }

    @Bean
    fun rsaPublicKey(): RSAPublicKey {
        return publicKeyResource.inputStream.use { inputStream ->
            requireNotNull(
                RsaKeyConverters.x509().convert(inputStream)
            )
        }
    }

    @Bean
    fun jwtEncoder(
        publicKey: RSAPublicKey,
        privateKey: RSAPrivateKey,
    ): JwtEncoder {
        return NimbusJwtEncoder
            .withKeyPair(
                publicKey,
                privateKey,
            )
            .build()
    }

    @Bean
    fun jwtDecoder(
        publicKey: RSAPublicKey,
    ): JwtDecoder {

        val decoder: NimbusJwtDecoder =
            NimbusJwtDecoder
                .withPublicKey(publicKey)
                .build()

        val validator: OAuth2TokenValidator<Jwt> =
            JwtValidators.createDefaultWithIssuer(
                issuer
            )

        decoder.setJwtValidator(
            validator
        )

        return decoder
    }
}
