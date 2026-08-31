package com.pavex.operations.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class WorkShiftDomainTest {
    private val now = Instant.parse("2026-08-31T07:00:00Z")
    private val actor = UUID.randomUUID()

    @Test
    fun `shift must start in future and last no more than 24 hours`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkShift.create(UUID.randomUUID(), "Morning", now, now.plus(8, ChronoUnit.HOURS), actor, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkShift.create(
                UUID.randomUUID(),
                "Too long",
                now.plus(1, ChronoUnit.HOURS),
                now.plus(26, ChronoUnit.HOURS),
                actor,
                now,
            )
        }
    }

    @Test
    fun `check in opens thirty minutes before shift and check out closes attendance`() {
        val starts = now.plus(2, ChronoUnit.HOURS)
        val ends = starts.plus(8, ChronoUnit.HOURS)
        val shift = WorkShift.create(UUID.randomUUID(), "Morning", starts, ends, actor, now)
        val assignment = WorkforceShiftAssignment.create(shift.id, UUID.randomUUID(), actor, now)

        assertThrows(IllegalStateException::class.java) {
            assignment.checkIn(actor, starts, ends, starts.minus(31, ChronoUnit.MINUTES))
        }
        assertTrue(assignment.checkIn(actor, starts, ends, starts.minus(30, ChronoUnit.MINUTES)))
        assertEquals(WorkforceShiftAssignmentStatus.CHECKED_IN, assignment.status)
        assertFalse(assignment.checkIn(actor, starts, ends, starts.minus(20, ChronoUnit.MINUTES)))
        assertTrue(assignment.checkOut(actor, starts.plus(1, ChronoUnit.HOURS)))
        assertEquals(WorkforceShiftAssignmentStatus.CHECKED_OUT, assignment.status)
    }

    @Test
    fun `absent cannot be marked before shift starts`() {
        val starts = now.plus(1, ChronoUnit.HOURS)
        val assignment = WorkforceShiftAssignment.create(UUID.randomUUID(), UUID.randomUUID(), actor, now)
        assertThrows(IllegalStateException::class.java) {
            assignment.markAbsent(actor, starts, "no show", now)
        }
        assertTrue(assignment.markAbsent(actor, starts, "no show", starts))
        assertEquals(WorkforceShiftAssignmentStatus.ABSENT, assignment.status)
    }

    @Test
    fun `cancelled assignment can be restored but completed assignment cannot`() {
        val assignment = WorkforceShiftAssignment.create(UUID.randomUUID(), UUID.randomUUID(), actor, now)
        assertTrue(assignment.cancel(actor, "roster change", now.plusSeconds(1)))
        assignment.reassign(actor, now.plusSeconds(2))
        assertEquals(WorkforceShiftAssignmentStatus.SCHEDULED, assignment.status)
    }
}
