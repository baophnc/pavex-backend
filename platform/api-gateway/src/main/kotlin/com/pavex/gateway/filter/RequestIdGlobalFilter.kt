package com.pavex.gateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class RequestIdGlobalFilter :
    GlobalFilter,
    Ordered {

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {

        val requestId =
            exchange.request.headers
                .getFirst(REQUEST_ID_HEADER)
                ?.trim()
                ?.takeIf(::isValidRequestId)
                ?: UUID.randomUUID().toString()

        val request =
            exchange.request
                .mutate()
                .headers { headers ->
                    headers.set(
                        REQUEST_ID_HEADER,
                        requestId,
                    )
                }
                .build()

        val updatedExchange =
            exchange
                .mutate()
                .request(request)
                .build()

        updatedExchange.response.headers.set(
            REQUEST_ID_HEADER,
            requestId,
        )

        return chain.filter(
            updatedExchange
        )
    }

    override fun getOrder(): Int =
        Ordered.HIGHEST_PRECEDENCE

    private fun isValidRequestId(
        value: String,
    ): Boolean {
        return value.length in 1..64 &&
            REQUEST_ID_PATTERN.matches(value)
    }

    companion object {
        const val REQUEST_ID_HEADER =
            "X-Request-Id"

        private val REQUEST_ID_PATTERN =
            Regex("^[A-Za-z0-9._:-]+$")
    }
}
