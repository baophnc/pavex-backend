package com.pavex.gateway.config

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.util.Base64

@Configuration
class GatewayConfig {
    /** The default for rate-limited routes that name no resolver. */
    @Bean("clientIpKeyResolver")
    @Primary
    fun clientIpKeyResolver(clientIps: TrustedProxyClientIpResolver): KeyResolver =
        KeyResolver { exchange -> Mono.just(clientIps.resolve(exchange)) }

    /**
     * Phones on mobile data often share one public IP (carrier NAT), so routes the field app hits
     * often, such as GPS batches, are limited per user. Requests without a readable token fall back
     * to the client IP. The token is not verified here; the service behind the route does that.
     */
    @Bean("userOrClientIpKeyResolver")
    fun userOrClientIpKeyResolver(clientIps: TrustedProxyClientIpResolver): KeyResolver =
        KeyResolver { exchange ->
            Mono.just(BearerSubject.of(exchange)?.let { "user:$it" } ?: "ip:${clientIps.resolve(exchange)}")
        }
}

internal object BearerSubject {
    private val mapper = JsonMapper.builder().build()
    private val SUBJECT = Regex("^[0-9a-fA-F-]{36}$")

    fun of(exchange: ServerWebExchange): String? =
        exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)?.let(::fromHeader)

    /** The `sub` of a bearer JWT when it is a user id, otherwise null. */
    fun fromHeader(header: String): String? {
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        val payload = header.substring(7).trim().split('.').takeIf { it.size == 3 }?.get(1) ?: return null
        return runCatching {
            mapper.readTree(Base64.getUrlDecoder().decode(payload)).get("sub")?.asString()
        }.getOrNull()?.takeIf(SUBJECT::matches)
    }
}
