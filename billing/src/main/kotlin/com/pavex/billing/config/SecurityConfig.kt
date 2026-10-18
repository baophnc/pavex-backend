package com.pavex.billing.config

import com.pavex.security.userTokenChain
import org.springframework.http.HttpMethod
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
            // payOS calls this without a user token; the payload signature is verified instead.
            it.requestMatchers(HttpMethod.POST, "/api/v1/integrations/payos/webhook").permitAll()
                .requestMatchers("/api/v1/billing/**", "/api/v1/admin/billing/**").authenticated()
                .anyRequest().denyAll()
        }
}
