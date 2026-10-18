package com.pavex.identity.config

import com.pavex.security.TrustedServiceTokens
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder

/**
 * The services that may call Identity's internal API. Auth signs its service tokens with the
 * user-token key (`security.jwt.*`); Partners and Operations use their own key pairs.
 */
@Configuration
class ServiceJwtConfig(
    private val trusted: TrustedServiceTokens,
) {
    @Bean("authServiceJwtDecoder")
    fun authServiceJwtDecoder(): JwtDecoder =
        trusted.decoder("security.jwt", AUTH_SERVICE, IDENTITY_SERVICE, IDENTITY_INTERNAL_SCOPE)

    @Bean("partnersServiceJwtDecoder")
    fun partnersServiceJwtDecoder(): JwtDecoder =
        trusted.decoder("security.service-jwt.partners", PARTNERS_SERVICE, IDENTITY_SERVICE, IDENTITY_MERCHANT_SCOPE)

    /** Each workforce endpoint then requires its own scope (SecurityConfig). */
    @Bean("operationsServiceJwtDecoder")
    fun operationsServiceJwtDecoder(): JwtDecoder =
        trusted.decoder("security.service-jwt.operations", OPERATIONS_SERVICE, IDENTITY_SERVICE, IDENTITY_WORKFORCE_READ_SCOPE)

    companion object {
        private const val AUTH_SERVICE = "auth-service"
        private const val PARTNERS_SERVICE = "partners-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val IDENTITY_SERVICE = "identity-service"
        private const val IDENTITY_INTERNAL_SCOPE = "identity.internal"
        private const val IDENTITY_MERCHANT_SCOPE = "identity.merchant"
        private const val IDENTITY_WORKFORCE_READ_SCOPE = "identity.workforce.read"
    }
}
