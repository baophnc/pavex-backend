package com.pavex.operations.config

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
    fun shipmentInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("shipmentServiceJwtDecoder") shipmentServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(shipmentServiceJwtDecoder, "/internal/v1/operations/shipments/**")

    @Bean
    @Order(0)
    fun billingInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("billingServiceJwtDecoder") billingServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(billingServiceJwtDecoder, "/internal/v1/operations/access/**")

    @Bean
    @Order(0)
    fun partnersInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("partnersServiceJwtDecoder") partnersServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(partnersServiceJwtDecoder, "/internal/v1/operations/partners/**")

    @Bean
    @Order(0)
    fun notificationsInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("notificationsServiceJwtDecoder") notificationsServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(notificationsServiceJwtDecoder, "/internal/v1/operations/notifications/**")

    @Bean
    @Order(0)
    fun fleetInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("fleetServiceJwtDecoder") fleetServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http.serviceTokenChain(fleetServiceJwtDecoder, "/internal/v1/operations/fleet/**")

    @Bean
    @Order(2)
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder") jwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.userTokenChain(jwtDecoder) {
            it.requestMatchers("/actuator/info", "/actuator/metrics", "/actuator/metrics/**")
                .hasAuthority("operations.assignments.manage")
                .requestMatchers("/api/v1/operations/**").authenticated()
                .requestMatchers("/api/v1/admin/operations/**").authenticated()
                .requestMatchers("/internal/**").denyAll()
                .anyRequest().denyAll()
        }
}
