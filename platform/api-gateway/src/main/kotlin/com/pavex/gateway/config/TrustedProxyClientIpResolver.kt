package com.pavex.gateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import java.net.InetAddress

@Component
class TrustedProxyClientIpResolver(
    @Value("\${gateway.client-ip.trusted-proxies:}") trustedProxySpec: String,
) {
    private val trustedNetworks: List<IpNetwork> = trustedProxySpec
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull(IpNetwork::parse)

    fun resolve(exchange: ServerWebExchange): String {
        val remote = exchange.request.remoteAddress?.address ?: return "unknown"
        if (!isTrusted(remote)) return remote.hostAddress

        val header = exchange.request.headers.getFirst("X-Forwarded-For")?.trim().orEmpty()
        if (header.isEmpty()) return remote.hostAddress
        val forwarded = header.split(',').map { parseForwardedAddress(it.trim()) ?: return remote.hostAddress }
        if (forwarded.isEmpty()) return remote.hostAddress

        val chain = forwarded + remote
        for (index in chain.indices.reversed()) {
            val address = chain[index]
            if (!isTrusted(address)) return address.hostAddress
        }
        return chain.first().hostAddress
    }

    private fun isTrusted(address: InetAddress): Boolean = trustedNetworks.any { it.contains(address) }

    private fun parseForwardedAddress(raw: String): InetAddress? {
        val value = when {
            raw.startsWith("[") && raw.contains(']') -> raw.substring(1, raw.indexOf(']'))
            raw.count { it == ':' } == 1 && raw.substringBefore(':').contains('.') -> raw.substringBefore(':')
            else -> raw
        }
        if (!NUMERIC_IP.matches(value)) return null
        return runCatching { InetAddress.getByName(value) }.getOrNull()
    }

    private data class IpNetwork(
        val network: ByteArray,
        val prefixLength: Int,
    ) {
        fun contains(address: InetAddress): Boolean {
            val candidate = address.address
            if (candidate.size != network.size) return false
            val fullBytes = prefixLength / 8
            val remainingBits = prefixLength % 8
            for (index in 0 until fullBytes) {
                if (candidate[index] != network[index]) return false
            }
            if (remainingBits == 0) return true
            val mask = (0xFF shl (8 - remainingBits)) and 0xFF
            return (candidate[fullBytes].toInt() and mask) == (network[fullBytes].toInt() and mask)
        }

        companion object {
            fun parse(raw: String): IpNetwork? {
                val host = raw.substringBefore('/')
                if (!NUMERIC_IP.matches(host)) return null
                val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
                val maximum = address.address.size * 8
                val prefix = raw.substringAfter('/', maximum.toString()).toIntOrNull() ?: return null
                if (prefix !in 0..maximum) return null
                return IpNetwork(address.address, prefix)
            }
        }
    }

    companion object {
        private val NUMERIC_IP = Regex("^[0-9A-Fa-f:.]+$")
    }
}
