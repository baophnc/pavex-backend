package com.pavex.shipment.config

import com.pavex.security.userTokenChain
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder") jwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http.userTokenChain(jwtDecoder) {
            it.requestMatchers("/api/v1/tracking/**").permitAll()
                .requestMatchers("/api/v1/shipments/**").authenticated()
                .requestMatchers("/api/v1/parcel-scans/**").authenticated()
                .requestMatchers("/api/v1/deliveries/**").authenticated()
                .requestMatchers("/api/v1/workforce/**").authenticated()
                .requestMatchers("/api/v1/admin/shipments/**", "/api/v1/admin/shipment-cases/**", "/api/v1/admin/control-tower/**", "/api/v1/admin/shipment-exceptions/**").authenticated()
                .requestMatchers("/internal/**").denyAll()
                .anyRequest().denyAll()
        }
}
