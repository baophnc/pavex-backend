package com.pavex.notifications.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder") jwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/actuator/health/**",
                    // Metrics for Prometheus on the internal network; served only where a deployment exposes them.
                    "/actuator/prometheus",
                ).permitAll()
                    .requestMatchers("/api/v1/notifications/**").authenticated()
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.decoder(jwtDecoder)
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .build()

    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> =
        Converter { jwt ->
            val authorities = buildList {
                jwt.getClaimAsStringList("roles").orEmpty().forEach {
                    add(SimpleGrantedAuthority("ROLE_$it"))
                }
                jwt.getClaimAsStringList("permissions").orEmpty().forEach {
                    add(SimpleGrantedAuthority(it))
                }
            }
            JwtAuthenticationToken(jwt, authorities, requireNotNull(jwt.subject))
        }
}
