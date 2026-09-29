package com.pavex.gateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class BearerSubjectTest {
    private val userId = "6f1c2d3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f"

    @Test
    fun `reads the user id from a bearer token`() {
        assertEquals(userId, BearerSubject.fromHeader("Bearer ${token("""{"sub":"$userId","typ":"user"}""")}"))
    }

    @Test
    fun `ignores anything that is not a user bearer token`() {
        assertNull(BearerSubject.fromHeader("Basic abc"))
        assertNull(BearerSubject.fromHeader("Bearer not-a-jwt"))
        assertNull(BearerSubject.fromHeader("Bearer ${token("""{"sub":"shipment-service"}""")}"))
        assertNull(BearerSubject.fromHeader("Bearer ${token("""{"typ":"user"}""")}"))
    }

    private fun token(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf("""{"alg":"RS256"}""", payload).joinToString(".") { encoder.encodeToString(it.toByteArray()) } + ".signature"
    }
}
