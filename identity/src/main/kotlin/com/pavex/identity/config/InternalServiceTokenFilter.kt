package com.pavex.identity.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class InternalServiceTokenFilter(
    @Value("\${identity.internal-token:}") private val expectedToken: String,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith(INTERNAL_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val supplied = request.getHeader(INTERNAL_TOKEN_HEADER).orEmpty()
        val valid = expectedToken.isNotBlank() && MessageDigest.isEqual(
            expectedToken.toByteArray(StandardCharsets.UTF_8),
            supplied.toByteArray(StandardCharsets.UTF_8),
        )
        if (!valid) {
            response.sendError(HttpStatus.UNAUTHORIZED.value())
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val INTERNAL_PREFIX = "/internal/"
        const val INTERNAL_TOKEN_HEADER = "X-Pavex-Internal-Token"
    }
}
