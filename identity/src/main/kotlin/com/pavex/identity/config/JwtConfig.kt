package com.pavex.identity.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.security.converter.RsaKeyConverters
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
class JwtConfig(
    @Value("\${security.jwt.public-key}") private val publicKeyResource: Resource,
    @Value("\${security.jwt.issuer}") private val issuer: String,
) {
    @Bean
    fun jwtDecoder(): JwtDecoder {
        val publicKey = publicKeyResource.inputStream.use {
            requireNotNull(RsaKeyConverters.x509().convert(it))
        }
        return NimbusJwtDecoder.withPublicKey(publicKey).build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer))
        }
    }
}
