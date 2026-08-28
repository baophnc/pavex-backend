package com.pavex.identity.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        internalServiceTokenFilter: InternalServiceTokenFilter,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                ).permitAll()
                    // InternalServiceTokenFilter authenticates this boundary for now.
                    .requestMatchers("/internal/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/identities/me").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/identities/me").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/me/profile").authenticated()
                    .requestMatchers("/api/v1/users/**", "/api/v1/roles/**").authenticated()
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .addFilterBefore(
                internalServiceTokenFilter,
                UsernamePasswordAuthenticationFilter::class.java,
            )
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
}
