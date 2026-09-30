package com.pavex.operations.service

import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.dto.ShipmentAssignmentPlan
import com.pavex.operations.dto.ShipmentAssignmentRouteLeg
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Clock
import java.util.UUID

class ManualDispatchModeTest {
    @Test
    fun `manual mode leaves pickup ready and later legs blocked without querying candidates`() {
        val assignments = mock(OperationalAssignmentRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findAllByShipmentIdOrderBySequenceAsc" -> emptyList<Any>()
                "saveAllAndFlush" -> invocation.arguments[0]
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
        val members = mock(WorkforceMemberRepository::class.java)
        val identity = mock(IdentityWorkforceClient::class.java)
        val service = OperationalAssignmentService(assignments,
            mock(OperationalAssignmentEventLogRepository::class.java), members,
            mock(HubMembershipRepository::class.java), mock(WorkforceAvailabilityRepository::class.java),
            mock(WorkforceShiftAssignmentRepository::class.java), mock(WorkShiftRepository::class.java),
            identity, mock(AssignmentLockService::class.java), mock(AssignmentDispatchQueue::class.java),
            Clock.systemUTC(), autoAssignEnabled = false)
        val hubs = List(4) { UUID.randomUUID() }
        val plan = ShipmentAssignmentPlan(UUID.randomUUID(), hubs.first(), hubs.last(),
            (0..2).map { ShipmentAssignmentRouteLeg(UUID.randomUUID(), it + 1, hubs[it], hubs[it + 1]) })

        val work = service.plan(plan)
        assertEquals(9, work.size)
        assertEquals(OperationalAssignmentStatus.READY, work.first().status)
        assertTrue(work.drop(1).all { it.status == OperationalAssignmentStatus.BLOCKED })
        assertTrue(work.all { it.assigneeWorkforceMemberId == null })
        assertFalse(service.redispatch(work.first().id))
        verifyNoInteractions(members, identity)
    }
}
