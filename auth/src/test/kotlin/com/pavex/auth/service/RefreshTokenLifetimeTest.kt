package com.pavex.auth.service

import com.pavex.auth.repository.RefreshSessionRepository
import com.pavex.auth.security.OpaqueTokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Duration
import java.time.Instant

class RefreshTokenLifetimeTest {
    private val login = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `new login gets the sliding refresh window`() {
        val service = service(ttl = Duration.ofDays(30), maxLifetime = Duration.ofDays(90))

        assertEquals(login.plus(Duration.ofDays(30)), service.expiresAt(now = login, sessionStartedAt = login))
    }

    @Test
    fun `rotation keeps sliding while inside the absolute lifetime`() {
        val service = service(ttl = Duration.ofDays(30), maxLifetime = Duration.ofDays(90))
        val now = login.plus(Duration.ofDays(20))

        assertEquals(now.plus(Duration.ofDays(30)), service.expiresAt(now = now, sessionStartedAt = login))
    }

    @Test
    fun `rotation near the end is capped at login plus max lifetime`() {
        val service = service(ttl = Duration.ofDays(30), maxLifetime = Duration.ofDays(90))
        val now = login.plus(Duration.ofDays(80))

        assertEquals(login.plus(Duration.ofDays(90)), service.expiresAt(now = now, sessionStartedAt = login))
    }

    @Test
    fun `max lifetime shorter than refresh ttl is rejected at startup`() {
        assertThrows(IllegalArgumentException::class.java) {
            service(ttl = Duration.ofDays(30), maxLifetime = Duration.ofDays(7))
        }
    }

    private fun service(ttl: Duration, maxLifetime: Duration) = RefreshTokenService(
        mock(RefreshSessionRepository::class.java),
        OpaqueTokenService(),
        mock(AdvisoryLockService::class.java),
        ttl,
        maxLifetime,
    )
}
