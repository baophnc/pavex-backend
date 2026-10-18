package com.pavex.identity.config

import com.pavex.security.serviceTokenChain
import com.pavex.security.userTokenChain
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    @Order(1)
    fun partnersMerchantSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("partnersServiceJwtDecoder")
        partnersServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.serviceTokenChain(
            partnersServiceJwtDecoder,
            "/internal/v1/identities/*/merchant-eligibility",
            "/internal/v1/identities/*/merchant-role",
        ) { it.anyRequest().hasAuthority(IDENTITY_MERCHANT_AUTHORITY) }

    @Bean
    @Order(2)
    fun operationsWorkforceSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("operationsServiceJwtDecoder")
        operationsServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.serviceTokenChain(operationsServiceJwtDecoder, "/internal/v1/operations/**") {
            it.requestMatchers("/internal/v1/operations/identities/snapshots").hasAuthority(IDENTITY_WORKFORCE_READ_AUTHORITY)
                .requestMatchers("/internal/v1/operations/identities/provision").hasAuthority(IDENTITY_WORKFORCE_PROVISION_AUTHORITY)
                .anyRequest().denyAll()
        }

    @Bean
    @Order(3)
    fun internalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("authServiceJwtDecoder")
        authServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.serviceTokenChain(authServiceJwtDecoder, "/internal/**") { it.anyRequest().hasAuthority(IDENTITY_INTERNAL_AUTHORITY) }

    @Bean
    @Order(4)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder")
        userJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.userTokenChain(userJwtDecoder) {
            it.requestMatchers(HttpMethod.GET, "/api/v1/identities/me").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/identities/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/me/profile").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/me/avatar").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/avatars/*").permitAll()
                .requestMatchers("/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**").authenticated()
                .anyRequest().denyAll()
        }

    companion object {
        private const val IDENTITY_INTERNAL_AUTHORITY = "SCOPE_identity.internal"
        private const val IDENTITY_MERCHANT_AUTHORITY = "SCOPE_identity.merchant"
        private const val IDENTITY_WORKFORCE_READ_AUTHORITY = "SCOPE_identity.workforce.read"
        private const val IDENTITY_WORKFORCE_PROVISION_AUTHORITY = "SCOPE_identity.workforce.provision"
    }
}
