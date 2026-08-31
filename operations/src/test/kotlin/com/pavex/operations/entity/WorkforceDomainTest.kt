package com.pavex.operations.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class WorkforceDomainTest {
    @Test
    fun `offboarded workforce member cannot be reactivated`() {
        val now = Instant.parse("2026-08-31T00:00:00Z")
        val member = WorkforceMember.create(UUID.randomUUID(), WorkforceMemberKind.COURIER, now)
        assertTrue(member.changeStatus(WorkforceMemberStatus.OFFBOARDED, now.plusSeconds(1)))
        assertThrows(IllegalStateException::class.java) {
            member.changeStatus(WorkforceMemberStatus.ACTIVE, now.plusSeconds(2))
        }
    }

    @Test
    fun `hub membership retains history when deactivated and reactivated`() {
        val now = Instant.parse("2026-08-31T00:00:00Z")
        val membership = HubMembership(
            workforceMemberId = UUID.randomUUID(),
            hubId = UUID.randomUUID(),
            isPrimary = true,
            assignedAt = now,
            updatedAt = now,
        )
        membership.deactivate(now.plusSeconds(1))
        assertFalse(membership.isActive)
        assertFalse(membership.isPrimary)
        membership.reactivate(primary = false, now = now.plusSeconds(2))
        assertTrue(membership.isActive)
        assertEquals(null, membership.unassignedAt)
    }

    @Test
    fun `availability capacity is limited to one through one hundred`() {
        val availability = WorkforceAvailability(
            workforceMemberId = UUID.randomUUID(),
            maxConcurrentAssignments = 8,
        )
        assertThrows(IllegalArgumentException::class.java) {
            availability.setMaximum(101, Instant.now())
        }
        assertTrue(availability.setMaximum(12, Instant.now()))
        assertEquals(12, availability.maxConcurrentAssignments)
    }
}
