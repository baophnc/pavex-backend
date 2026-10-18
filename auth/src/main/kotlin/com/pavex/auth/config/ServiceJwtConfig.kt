package com.pavex.auth.config

import com.pavex.security.TrustedServiceTokens
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder

/** Identity is the only caller of Auth's internal API. */
@Configuration
class ServiceJwtConfig(
    private val trusted: TrustedServiceTokens,
) {
    @Bean("identityServiceJwtDecoder")
    fun identityServiceJwtDecoder(): JwtDecoder =
        trusted.decoder("security.service-jwt.identity", IDENTITY_SERVICE, AUTH_SERVICE, AUTH_INTERNAL_SCOPE)

    companion object {
        private const val IDENTITY_SERVICE = "identity-service"
        private const val AUTH_SERVICE = "auth-service"
        private const val AUTH_INTERNAL_SCOPE = "auth.internal"
    }
}
