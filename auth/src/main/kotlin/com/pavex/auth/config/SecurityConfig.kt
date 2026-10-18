package com.pavex.auth.config

import com.pavex.security.serviceTokenChain
import com.pavex.security.userTokenChain
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    @Order(1)
    fun internalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("identityServiceJwtDecoder")
        identityServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.serviceTokenChain(identityServiceJwtDecoder, "/internal/**") { it.anyRequest().hasAuthority(AUTH_INTERNAL_AUTHORITY) }

    @Bean
    @Order(2)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder")
        userJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.userTokenChain(userJwtDecoder) {
            it.requestMatchers(
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/verify-email",
                "/api/v1/auth/resend-verification",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password",
                "/api/v1/auth/accept-invite",
                "/api/v1/auth/google",
            ).permitAll()
                .anyRequest()
                .authenticated()
        }

    companion object {
        private const val AUTH_INTERNAL_AUTHORITY = "SCOPE_auth.internal"
    }
}
