package com.pavex.operations.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class OperationalAssignmentDomainTest {
    @Test
    fun `assignment follows worker lifecycle`() {
        val now = Instant.parse("2026-08-31T08:00:00Z")
        val memberId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val assignment = OperationalAssignment.create(
            shipmentId = UUID.randomUUID(),
            sequence = 1,
            routeLegId = null,
            type = OperationalAssignmentType.PICKUP,
            requiredKind = WorkforceMemberKind.COURIER,
            hubId = UUID.randomUUID(),
            priority = 70,
            now = now,
        )

        val assignmentActor = UUID.randomUUID()
        assignment.assign(memberId, assignmentActor, now.plusSeconds(1))
        assertEquals(OperationalAssignmentStatus.ASSIGNED, assignment.status)
        assertEquals(assignmentActor, assignment.lastActorUserId)
        assignment.accept(memberId, now.plusSeconds(2))
        assignment.start(memberId, now.plusSeconds(3))
        assignment.complete(memberId, "Delivered to operations handoff.", now.plusSeconds(4))

        assertEquals(OperationalAssignmentStatus.COMPLETED, assignment.status)
        assertEquals("Delivered to operations handoff.", assignment.resolutionNote)
        assertTrue(assignment.completedAt != null)
    }

    @Test
    fun `reject clears assignee but preserves rejected state for manual redispatch`() {
        val now = Instant.parse("2026-08-31T08:00:00Z")
        val memberId = UUID.randomUUID()
        val assignment = OperationalAssignment.create(
            UUID.randomUUID(), 1, null, OperationalAssignmentType.LAST_MILE_DELIVERY,
            WorkforceMemberKind.COURIER, UUID.randomUUID(), 50, now,
        )
        assignment.assign(memberId, null, now)
        assignment.reject(memberId, "Vehicle issue", now.plusSeconds(1))

        assertEquals(OperationalAssignmentStatus.REJECTED, assignment.status)
        assertNull(assignment.assigneeWorkforceMemberId)
        assertEquals("Vehicle issue", assignment.resolutionNote)
    }

    @Test
    fun `in progress assignment cannot be reassigned or cancelled after completion`() {
        val now = Instant.parse("2026-08-31T08:00:00Z")
        val memberId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val assignment = OperationalAssignment.create(
            UUID.randomUUID(), 1, null, OperationalAssignmentType.ORIGIN_HUB_HANDLING,
            WorkforceMemberKind.WAREHOUSE_OPERATOR, UUID.randomUUID(), 50, now,
        )
        assignment.assign(memberId, userId, now)
        assignment.accept(memberId, now)
        assignment.start(memberId, now)

        assertThrows(IllegalStateException::class.java) {
            assignment.assign(UUID.randomUUID(), userId, now.plusSeconds(1))
        }

        assignment.complete(memberId, null, now.plusSeconds(2))
        assertThrows(IllegalStateException::class.java) {
            assignment.cancel("Too late", now.plusSeconds(3))
        }
    }
}
