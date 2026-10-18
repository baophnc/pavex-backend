package com.pavex.operations.config

import com.pavex.common.security.PavexJwtClaims
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
    fun shipmentInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("shipmentServiceJwtDecoder") shipmentServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/v1/operations/shipments/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.decoder(shipmentServiceJwtDecoder) }
            }
            .build()

    @Bean
    @Order(0)
    fun billingInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("billingServiceJwtDecoder") billingServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/v1/operations/access/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.decoder(billingServiceJwtDecoder) }
            }
            .build()

    @Bean
    @Order(0)
    fun partnersInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("partnersServiceJwtDecoder") partnersServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/v1/operations/partners/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.decoder(partnersServiceJwtDecoder) }
            }
            .build()

    @Bean
    @Order(0)
    fun notificationsInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("notificationsServiceJwtDecoder") notificationsServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/v1/operations/notifications/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.decoder(notificationsServiceJwtDecoder) }
            }
            .build()

    @Bean
    @Order(0)
    fun fleetInternalSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("fleetServiceJwtDecoder") fleetServiceJwtDecoder: JwtDecoder,
    ): SecurityFilterChain =
        http
            .securityMatcher("/internal/v1/operations/fleet/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.decoder(fleetServiceJwtDecoder) }
            }
            .build()

    @Bean
    @Order(2)
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("operationsUserJwtDecoder") jwtDecoder: JwtDecoder,
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
                    .requestMatchers("/actuator/info", "/actuator/metrics", "/actuator/metrics/**")
                    .hasAuthority("operations.assignments.manage")
                    .requestMatchers("/api/v1/operations/**").authenticated()
                    .requestMatchers("/api/v1/admin/operations/**").authenticated()
                    .requestMatchers("/internal/**").denyAll()
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
                jwt.getClaimAsStringList(PavexJwtClaims.ROLES).orEmpty().forEach {
                    add(SimpleGrantedAuthority("ROLE_$it"))
                }
                jwt.getClaimAsStringList(PavexJwtClaims.PERMISSIONS).orEmpty().forEach {
                    add(SimpleGrantedAuthority(it))
                }
            }
            JwtAuthenticationToken(jwt, authorities, requireNotNull(jwt.subject))
        }
}
