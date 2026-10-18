package com.pavex.network.config

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
    fun pricingInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("pricingServiceJwtDecoder") pricingServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(pricingServiceJwtDecoder, "/internal/v1/network/locations/**")

    @Bean
    @Order(2)
    fun shipmentInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("shipmentServiceJwtDecoder") shipmentServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(shipmentServiceJwtDecoder, "/internal/v1/network/shipments/**")

    @Bean
    @Order(3)
    fun operationsInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("operationsServiceJwtDecoder") operationsServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(operationsServiceJwtDecoder, "/internal/v1/network/operations/**")

    @Bean
    @Order(4)
    fun fleetInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("fleetServiceJwtDecoder") fleetServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(fleetServiceJwtDecoder, "/internal/v1/network/fleet/**")

    @Bean
    @Order(5)
    fun partnersInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("partnersServiceJwtDecoder") partnersServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(partnersServiceJwtDecoder, "/internal/v1/network/partners/**")

    @Bean
    @Order(6)
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder") jwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.userTokenChain(jwtDecoder) {
            it.requestMatchers(HttpMethod.GET, "/api/v1/network/hubs").permitAll()
                .requestMatchers("/api/v1/admin/network/**").authenticated()
                .requestMatchers("/internal/**").denyAll()
                .anyRequest().denyAll()
        }
}
