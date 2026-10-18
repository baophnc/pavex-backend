package com.pavex.security

import com.pavex.common.security.PavexJwtClaims
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

typealias AuthorizationRules = AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry

/**
 * Served without a token by every service: OpenAPI docs, health, and metrics for Prometheus on the
 * internal network (only where a deployment exposes them).
 */
val PUBLIC_PLATFORM_PATHS: List<String> = listOf(
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/actuator/health",
    "/actuator/health/**",
    "/actuator/prometheus",
)

/**
 * Stateless chain for the user-facing API, checked with [decoder] (normally `userJwtDecoder`).
 * [PUBLIC_PLATFORM_PATHS] are open; [rules] decide the rest. Authorities come from
 * [PavexJwtAuthenticationConverter].
 */
fun HttpSecurity.userTokenChain(decoder: JwtDecoder, rules: (AuthorizationRules) -> Unit): SecurityFilterChain =
    csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(*PUBLIC_PLATFORM_PATHS.toTypedArray()).permitAll()
            rules(it)
        }
        .oauth2ResourceServer { resourceServer ->
            resourceServer.jwt { jwt ->
                jwt.decoder(decoder)
                jwt.jwtAuthenticationConverter(PavexJwtAuthenticationConverter())
            }
        }
        .build()

/**
 * Stateless chain for the `/internal` [paths] other services call, checked with a service-token
 * [decoder]. Spring's default converter applies, so the token's scopes become `SCOPE_<scope>`
 * authorities for [rules]; by default any valid token is enough.
 */
fun HttpSecurity.serviceTokenChain(
    decoder: JwtDecoder,
    vararg paths: String,
    rules: (AuthorizationRules) -> Unit = { it.anyRequest().authenticated() },
): SecurityFilterChain =
    securityMatcher(*paths)
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { rules(it) }
        .oauth2ResourceServer { resourceServer -> resourceServer.jwt { it.decoder(decoder) } }
        .build()

/** A person's token: each role in `roles` becomes `ROLE_<role>`, each entry in `permissions` an authority as is. */
class PavexJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val authorities = buildList {
            jwt.getClaimAsStringList(PavexJwtClaims.ROLES).orEmpty().forEach { add(SimpleGrantedAuthority("ROLE_$it")) }
            jwt.getClaimAsStringList(PavexJwtClaims.PERMISSIONS).orEmpty().forEach { add(SimpleGrantedAuthority(it)) }
        }
        return JwtAuthenticationToken(jwt, authorities, requireNotNull(jwt.subject) { "JWT subject is missing" })
    }
}
