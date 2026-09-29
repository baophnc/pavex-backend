package com.pavex.billing.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.security.converter.RsaKeyConverters
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

@Configuration
class ServiceJwtConfig(
    @Value("\${security.service-jwt.private-key}")
    private val privateKeyResource: Resource,
    @Value("\${security.service-jwt.public-key}")
    private val publicKeyResource: Resource,
) {
    @Bean("billingServiceJwtEncoder")
    fun billingServiceJwtEncoder(): JwtEncoder {
        val privateKey = privateKeyResource.inputStream.use {
            requireNotNull(RsaKeyConverters.pkcs8().convert(it))
        }
        val publicKey = publicKeyResource.inputStream.use {
            requireNotNull(RsaKeyConverters.x509().convert(it))
        }
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build()
    }
}
