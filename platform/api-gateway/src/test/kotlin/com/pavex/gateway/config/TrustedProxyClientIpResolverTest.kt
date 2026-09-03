package com.pavex.gateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import java.net.InetSocketAddress

class TrustedProxyClientIpResolverTest {
    @Test
    fun `ignores spoofed forwarded header when direct peer is not trusted`() {
        val resolver = TrustedProxyClientIpResolver("10.0.0.0/8")
        val exchange = exchange("203.0.113.10", "198.51.100.25")

        assertEquals("203.0.113.10", resolver.resolve(exchange))
    }

    @Test
    fun `returns first untrusted client behind trusted proxy chain`() {
        val resolver = TrustedProxyClientIpResolver("10.0.0.0/8,192.168.0.0/16")
        val exchange = exchange("10.0.0.8", "198.51.100.25, 192.168.1.20")

        assertEquals("198.51.100.25", resolver.resolve(exchange))
    }

    @Test
    fun `falls back to remote peer when forwarded chain is malformed`() {
        val resolver = TrustedProxyClientIpResolver("10.0.0.0/8")
        val exchange = exchange("10.0.0.8", "spoofed.example, 198.51.100.25")

        assertEquals("10.0.0.8", resolver.resolve(exchange))
    }

    private fun exchange(remoteIp: String, forwardedFor: String): MockServerWebExchange {
        val request = MockServerHttpRequest.get("/")
            .remoteAddress(InetSocketAddress(remoteIp, 12345))
            .header("X-Forwarded-For", forwardedFor)
            .build()
        return MockServerWebExchange.from(request)
    }
}
