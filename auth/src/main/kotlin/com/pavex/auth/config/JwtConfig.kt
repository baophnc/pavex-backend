package com.pavex.auth.config

import com.pavex.security.RsaKeys
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.security.oauth2.jwt.JwtEncoder

/**
 * Auth signs the user access tokens (and its own service tokens) with `security.jwt.private-key`.
 * The `userJwtDecoder` that checks them comes from platform-security, like in every service.
 */
@Configuration
class JwtConfig(
    @Value("\${security.jwt.private-key}")
    private val privateKeyResource: Resource,
    @Value("\${security.jwt.public-key}")
    private val publicKeyResource: Resource,
) {
    @Bean
    fun jwtEncoder(): JwtEncoder =
        RsaKeys.encoder(RsaKeys.publicKey(publicKeyResource), RsaKeys.privateKey(privateKeyResource))
}
