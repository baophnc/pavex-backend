package com.pavex.partners.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.core.annotation.Order
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    @Order(1)
    fun internalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("shipmentServiceJwtDecoder") shipmentServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/v1/merchants/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.anyRequest().hasAuthority("SCOPE_partners.orders.evaluate")
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.decoder(shipmentServiceJwtDecoder) }
            }
            .build()

    @Bean
    @Order(2)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder") userJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/actuator/health/**",
                ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/locations/**").permitAll()
                    .requestMatchers(
                        "/api/v1/partners/**",
                        "/api/v1/merchants/**",
                        "/api/v1/admin/merchants/**",
                    ).authenticated()
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.decoder(userJwtDecoder)
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .build()

    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> =
        Converter { jwt ->
            println("JWT SUBJECT = ${jwt.subject}")
            println("JWT ROLES = ${jwt.getClaimAsStringList("roles")}")
            println("JWT PERMISSIONS = ${jwt.getClaimAsStringList("permissions")}")

            val authorities = mutableListOf<SimpleGrantedAuthority>()

            jwt.getClaimAsStringList("roles")
                .orEmpty()
                .forEach { role ->
                    authorities += SimpleGrantedAuthority("ROLE_$role")
                }

            jwt.getClaimAsStringList("permissions")
                .orEmpty()
                .forEach { permission ->
                    authorities += SimpleGrantedAuthority(permission)
                }

            println("AUTHORITIES = $authorities")

            JwtAuthenticationToken(
                jwt,
                authorities,
                requireNotNull(jwt.subject),
            )
        }
}
