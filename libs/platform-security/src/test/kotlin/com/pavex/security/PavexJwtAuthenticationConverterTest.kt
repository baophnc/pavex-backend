package com.pavex.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/** Moved from operations' SecurityConfigTest together with the converter. */
class PavexJwtAuthenticationConverterTest {
    @Test
    fun `jwt converter maps roles and permissions into authorities`() {
        val jwt = jwt(
            mapOf(
                "sub" to "00000000-0000-0000-0000-000000000001",
                "roles" to listOf("OPERATIONS_MANAGER"),
                "permissions" to listOf(
                    "operations.workforce.manage",
                    "operations.assignments.manage",
                ),
            ),
        )

        val authentication = PavexJwtAuthenticationConverter().convert(jwt)
        val authorities = authentication.authorities.map { it.authority }.toSet()

        assertEquals(jwt.subject, authentication.name)
        assertTrue("ROLE_OPERATIONS_MANAGER" in authorities)
        assertTrue("operations.workforce.manage" in authorities)
        assertTrue("operations.assignments.manage" in authorities)
    }

    @Test
    fun `token without roles or permissions has no authorities and a token without subject is refused`() {
        assertTrue(PavexJwtAuthenticationConverter().convert(jwt(mapOf("sub" to "u1"))).authorities.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            PavexJwtAuthenticationConverter().convert(jwt(mapOf("roles" to listOf("ADMIN"))))
        }
    }

    private fun jwt(claims: Map<String, Any>) =
        Jwt("token", Instant.now(), Instant.now().plusSeconds(60), mapOf("alg" to "RS256"), claims)
}
