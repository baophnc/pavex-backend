package com.pavex.outbox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

class JdbcOutboxLeaseTest {
    private val jdbc = mock(JdbcTemplate::class.java)
    private val lease = JdbcOutboxLease(jdbc, "Test outbox row", SELECT_DUE, LEASE)
    private val mapper = RowMapper<UUID> { _, _ -> UUID.randomUUID() }
    private val now = Instant.parse("2026-10-18T08:00:00Z")
    private val lockedUntil = Timestamp.from(now.plusSeconds(30))
    private val lockId = UUID.randomUUID()
    private val rows = listOf(UUID.randomUUID(), UUID.randomUUID())

    @Test
    fun `due rows are selected with the batch limit and each one is leased to this dispatcher`() {
        selectReturns(rows)
        rows.forEach { `when`(jdbc.update(LEASE, lockId, lockedUntil, it)).thenReturn(1) }

        assertEquals(rows, claim())
        rows.forEach { verify(jdbc).update(LEASE, lockId, lockedUntil, it) }
    }

    @Test
    fun `nothing due leases nothing`() {
        selectReturns(emptyList())
        assertTrue(claim().isEmpty())
    }

    @Test
    fun `a row that cannot be leased fails the claim so its transaction rolls back`() {
        selectReturns(rows)
        `when`(jdbc.update(LEASE, lockId, lockedUntil, rows.first())).thenReturn(0)

        val failure = assertThrows(IllegalStateException::class.java) { claim() }
        assertEquals("Test outbox row ${rows.first()} could not be locked.", failure.message)
        verify(jdbc, never()).update(LEASE, lockId, lockedUntil, rows.last())
    }

    private fun claim() = lease.claim(lockId, now, 10, Duration.ofSeconds(30), mapper) { it }

    private fun selectReturns(result: List<UUID>) {
        `when`(jdbc.query(SELECT_DUE, mapper, Timestamp.from(now), Timestamp.from(now), 10)).thenReturn(result)
    }

    companion object {
        private const val SELECT_DUE = "SELECT id FROM t WHERE due FOR UPDATE SKIP LOCKED"
        private const val LEASE = "UPDATE t SET lock_id = ?, locked_until = ? WHERE id = ?"
    }
}
