package com.pavex.auth.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
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
    @Order(1)
    fun internalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("identityServiceJwtDecoder")
        identityServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/**")
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                it.anyRequest().hasAuthority(AUTH_INTERNAL_AUTHORITY)
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.decoder(identityServiceJwtDecoder)
                }
            }
            .build()

    @Bean
    @Order(2)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("userJwtDecoder")
        userJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
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
                    "/actuator/health",
                    "/actuator/health/**",
                ).permitAll()
                    .anyRequest()
                    .authenticated()
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

            JwtAuthenticationToken(
                jwt,
                authorities,
                requireNotNull(jwt.subject) { "JWT subject is missing" },
            )
        }

    companion object {
        private const val AUTH_INTERNAL_AUTHORITY = "SCOPE_auth.internal"
    }
}
