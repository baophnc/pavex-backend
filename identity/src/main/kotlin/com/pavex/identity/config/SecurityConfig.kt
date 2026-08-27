package com.pavex.identity.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {

        http
            .csrf {
                it.disable()
            }

            .sessionManagement {
                it.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
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
                ).permitAll()

                    .anyRequest()
                    .authenticated()
            }

            .oauth2ResourceServer {
                it.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter()
                    )
                }
            }

        return http.build()
    }


    @Bean
    fun jwtAuthenticationConverter():
        Converter<Jwt, AbstractAuthenticationToken> {

        return Converter { jwt ->

            val authorities =
                mutableListOf<SimpleGrantedAuthority>()

            val roles =
                jwt.getClaimAsStringList(
                    "roles"
                ) ?: emptyList()

            roles.forEach { role ->

                authorities.add(
                    SimpleGrantedAuthority(
                        "ROLE_$role"
                    )
                )
            }

            val permissions =
                jwt.getClaimAsStringList(
                    "permissions"
                ) ?: emptyList()

            permissions.forEach {
                permission ->

                authorities.add(
                    SimpleGrantedAuthority(
                        permission
                    )
                )
            }

            val subject = requireNotNull(jwt.subject) {
                "JWT subject is missing"
            }

            JwtAuthenticationToken(
                jwt,
                authorities,
                subject,
            )
        }
    }
}
