package com.pavex.partners.config

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
    fun internalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("internalServiceJwtDecoder") internalServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(internalServiceJwtDecoder, "/internal/v1/**")

    @Bean
    @Order(2)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder") userJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.userTokenChain(userJwtDecoder) {
            it.requestMatchers(HttpMethod.GET, "/api/v1/locations/**").permitAll()
                // Contact, support, claim and career forms work without an account.
                .requestMatchers(HttpMethod.POST, "/api/v1/support/requests").permitAll()
                .requestMatchers("/api/v1/admin/support/**").authenticated()
                .requestMatchers(
                    "/api/v1/partners/**",
                    "/api/v1/merchants/**",
                    "/api/v1/admin/merchants/**",
                ).authenticated()
                .anyRequest().denyAll()
        }
}
