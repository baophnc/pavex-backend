package com.pavex.operations.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class SecurityConfigTest {
    @Test
    fun `jwt converter maps roles and permissions into authorities`() {
        val jwt = Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            mapOf("alg" to "RS256"),
            mapOf(
                "sub" to "00000000-0000-0000-0000-000000000001",
                "roles" to listOf("OPERATIONS_MANAGER"),
                "permissions" to listOf(
                    "operations.workforce.manage",
                    "operations.assignments.manage",
                ),
            ),
        )

        val authentication = SecurityConfig().jwtAuthenticationConverter().convert(jwt)!!
        val authorities = authentication.authorities.map { it.authority }.toSet()

        assertEquals(jwt.subject, authentication.name)
        assertTrue("ROLE_OPERATIONS_MANAGER" in authorities)
        assertTrue("operations.workforce.manage" in authorities)
        assertTrue("operations.assignments.manage" in authorities)
    }
}
