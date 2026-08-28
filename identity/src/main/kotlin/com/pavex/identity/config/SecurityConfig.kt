package com.pavex.identity.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        internalServiceTokenFilter: InternalServiceTokenFilter,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/internal/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/identities/me").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/identities/me").authenticated()
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { it.jwt { } }
            .addFilterBefore(internalServiceTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
