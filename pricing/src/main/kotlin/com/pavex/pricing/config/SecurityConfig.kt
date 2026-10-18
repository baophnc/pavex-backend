package com.pavex.pricing.config

import com.pavex.security.serviceTokenChain
import com.pavex.security.userTokenChain
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
    @ConditionalOnProperty(prefix = "pricing.shipment", name = ["integration-enabled"], havingValue = "true")
    fun shipmentInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("shipmentServiceJwtDecoder") jwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(jwtDecoder, "/internal/v1/pricing/**")

    @Bean
    @Order(2)
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder") jwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.userTokenChain(jwtDecoder) {
            it.requestMatchers(HttpMethod.POST, "/api/v1/pricing/quotes").permitAll()
                .requestMatchers("/api/v1/admin/pricing/**").authenticated()
                .requestMatchers("/internal/**").denyAll()
                .anyRequest().denyAll()
        }
}
