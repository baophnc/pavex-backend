package com.pavex.gateway.config

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

@Configuration
class GatewayConfig {

    @Bean("clientIpKeyResolver")
    fun clientIpKeyResolver(): KeyResolver {
        return KeyResolver { exchange ->
            val clientIp =
                exchange.request
                    .remoteAddress
                    ?.address
                    ?.hostAddress
                    ?: "unknown"

            Mono.just(clientIp)
        }
    }
}
