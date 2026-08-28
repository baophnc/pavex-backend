package com.pavex.identity.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
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

    /**
     * Partners receives the narrowest Identity permission possible. It may read
     * merchant-application eligibility only; its service token cannot use the
     * broader Auth-owned /internal identity endpoints.
     */
    @Bean
    @Order(1)
    fun partnersMerchantSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("partnersServiceJwtDecoder")
        partnersServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher(
                "/internal/v1/identities/*/merchant-eligibility"
            )
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                it.anyRequest().hasAuthority(IDENTITY_MERCHANT_AUTHORITY)
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.decoder(partnersServiceJwtDecoder)
                }
            }
            .build()

    @Bean
    @Order(2)
    fun internalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("authServiceJwtDecoder")
        authServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/**")
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                it.anyRequest().hasAuthority(IDENTITY_INTERNAL_AUTHORITY)
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.decoder(authServiceJwtDecoder)
                }
            }
            .build()

    @Bean
    @Order(3)
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
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/actuator/health/**",
                ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/identities/me").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/identities/me").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/me/profile").authenticated()
                    .requestMatchers("/api/v1/users/**", "/api/v1/roles/**").authenticated()
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
        private const val IDENTITY_INTERNAL_AUTHORITY = "SCOPE_identity.internal"
        private const val IDENTITY_MERCHANT_AUTHORITY = "SCOPE_identity.merchant"
    }
}
