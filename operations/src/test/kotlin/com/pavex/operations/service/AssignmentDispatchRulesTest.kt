package com.pavex.operations.service

import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.ShipmentAssignmentAccessRequest
import com.pavex.contracts.operations.WorkforceIdentitySnapshot
import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.entity.HubMembership
import com.pavex.operations.entity.OperationalAssignment
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.entity.OperationalAssignmentType
import com.pavex.operations.entity.WorkforceMember
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.exception.OperationsConflictException
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.OperationalAssignmentEventLogRepository
import com.pavex.operations.repository.OperationalAssignmentRepository
import com.pavex.operations.repository.WorkShiftRepository
import com.pavex.operations.repository.WorkforceAvailabilityRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import com.pavex.operations.repository.WorkforceShiftAssignmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class AssignmentDispatchRulesTest {
    private val now = Instant.parse("2026-10-01T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val hubId = UUID.randomUUID()

    @Test
    fun `hub managers stand in for clerks but not for couriers or drivers`() {
        assertEquals(
            setOf(WorkforceMemberKind.WAREHOUSE_OPERATOR, WorkforceMemberKind.HUB_MANAGER),
            OperationalAssignmentService.eligibleKinds(WorkforceMemberKind.WAREHOUSE_OPERATOR),
        )
        assertEquals(setOf(WorkforceMemberKind.COURIER), OperationalAssignmentService.eligibleKinds(WorkforceMemberKind.COURIER))
        assertEquals(
            setOf(WorkforceMemberKind.LINE_HAUL_DRIVER),
            OperationalAssignmentService.eligibleKinds(WorkforceMemberKind.LINE_HAUL_DRIVER),
        )
    }

    @Test
    fun `clerks go before the manager, and people the job was taken from go last`() {
        val clerk = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val busyClerk = UUID.fromString("00000000-0000-0000-0000-00000000000b")
        val releasedClerk = UUID.fromString("00000000-0000-0000-0000-00000000000c")
        val manager = UUID.fromString("00000000-0000-0000-0000-00000000000d")
        val ordered = listOf(
            OperationalAssignmentService.CandidateLoad(manager, load = 0, maximum = 5, fallback = true),
            OperationalAssignmentService.CandidateLoad(releasedClerk, load = 0, maximum = 5, avoided = true),
            OperationalAssignmentService.CandidateLoad(busyClerk, load = 3, maximum = 5),
            OperationalAssignmentService.CandidateLoad(clerk, load = 1, maximum = 5),
        ).sortedWith(OperationalAssignmentService.CANDIDATE_ORDER).map { it.memberId }

        assertEquals(listOf(clerk, busyClerk, releasedClerk, manager), ordered)
    }

    @Test
    fun `a hub manager scanning at a post office without clerks takes over ready handling`() {
        val manager = member(WorkforceMemberKind.HUB_MANAGER)
        val assignment = handling(OperationalAssignmentStatus.READY)

        val result = service(assignment, manager, roles = listOf("HUB_MANAGER"))
            .claimHubHandlingFromShipment(assignment.shipmentId, assignment.id, claimBy(manager, assignment))

        assertEquals(OperationalAssignmentStatus.IN_PROGRESS, result.status)
        assertEquals(manager.id, result.assigneeWorkforceMemberId)
        assertEquals(manager.userId, assignment.assignedByUserId)
    }

    @Test
    fun `a scan takes over handling someone was given but has not accepted`() {
        val clerk = member(WorkforceMemberKind.WAREHOUSE_OPERATOR)
        val assignment = handling(OperationalAssignmentStatus.READY).also { it.assign(UUID.randomUUID(), null, now) }

        val result = service(assignment, clerk, roles = listOf("WAREHOUSE_OPERATOR"))
            .claimHubHandlingFromShipment(assignment.shipmentId, assignment.id, claimBy(clerk, assignment))

        assertEquals(OperationalAssignmentStatus.IN_PROGRESS, result.status)
        assertEquals(clerk.id, result.assigneeWorkforceMemberId)
    }

    @Test
    fun `accepted handling stays with whoever accepted it`() {
        val owner = UUID.randomUUID()
        val clerk = member(WorkforceMemberKind.WAREHOUSE_OPERATOR)
        val assignment = handling(OperationalAssignmentStatus.READY).also {
            it.assign(owner, null, now)
            it.accept(owner, now)
        }

        val result = service(assignment, clerk, roles = listOf("WAREHOUSE_OPERATOR"))
            .claimHubHandlingFromShipment(assignment.shipmentId, assignment.id, claimBy(clerk, assignment))

        assertEquals(OperationalAssignmentStatus.ACCEPTED, result.status)
        assertEquals(owner, result.assigneeWorkforceMemberId)
    }

    @Test
    fun `handling waiting on an earlier step cannot be taken over`() {
        val clerk = member(WorkforceMemberKind.WAREHOUSE_OPERATOR)
        val assignment = handling(OperationalAssignmentStatus.BLOCKED)

        val error = assertThrows<OperationsConflictException> {
            service(assignment, clerk, roles = listOf("WAREHOUSE_OPERATOR"))
                .claimHubHandlingFromShipment(assignment.shipmentId, assignment.id, claimBy(clerk, assignment))
        }
        assertEquals("assignment_not_ready", error.code)
    }

    @Test
    fun `a courier cannot take over hub handling`() {
        val courier = member(WorkforceMemberKind.COURIER)
        val assignment = handling(OperationalAssignmentStatus.READY)

        assertThrows<Exception> {
            service(assignment, courier, roles = listOf("COURIER"))
                .claimHubHandlingFromShipment(assignment.shipmentId, assignment.id, claimBy(courier, assignment))
        }
        assertEquals(OperationalAssignmentStatus.READY, assignment.status)
    }

    @Test
    fun `a job nobody can take goes to the back of the queue`() {
        val assignment = handling(OperationalAssignmentStatus.READY)
        val calls = mutableListOf<String>()

        assertFalse(service(assignment, null, roles = emptyList(), calls = calls).redispatch(assignment.id))
        assertTrue("markDispatchAttempt" in calls)
    }

    @Test
    fun `automatic jobs not accepted in time go back to the queue, hand-picked ones stay`() {
        val stale = handling(OperationalAssignmentStatus.READY).also { it.assign(UUID.randomUUID(), null, now.minus(Duration.ofMinutes(16))) }
        val fresh = handling(OperationalAssignmentStatus.READY).also { it.assign(UUID.randomUUID(), null, now.minus(Duration.ofMinutes(5))) }
        val handPicked = handling(OperationalAssignmentStatus.READY)
            .also { it.assign(UUID.randomUUID(), UUID.randomUUID(), now.minus(Duration.ofHours(2))) }

        assertTrue(queue(stale).releaseIfUnaccepted(stale.id))
        assertEquals(OperationalAssignmentStatus.READY, stale.status)
        assertEquals(null, stale.assigneeWorkforceMemberId)
        assertFalse(queue(fresh).releaseIfUnaccepted(fresh.id))
        assertFalse(queue(handPicked).releaseIfUnaccepted(handPicked.id))
        assertEquals(OperationalAssignmentStatus.ASSIGNED, handPicked.status)
    }

    @Test
    fun `leaving a shift returns only work not yet accepted`() {
        val memberId = UUID.randomUUID()
        val waiting = handling(OperationalAssignmentStatus.READY).also { it.assign(memberId, null, now) }
        val saved = mutableListOf<Any?>()
        val assignments = mock(OperationalAssignmentRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findAllByAssigneeWorkforceMemberIdInAndStatusIn" -> listOf(waiting)
                "saveAllAndFlush" -> invocation.arguments[0].also { saved += it }
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
        val queue = AssignmentDispatchQueue(
            assignments,
            mock(HubMembershipRepository::class.java),
            mock(AssignmentLockService::class.java),
            clock,
        )

        assertEquals(1, queue.releaseUnaccepted(listOf(memberId), now))
        assertEquals(OperationalAssignmentStatus.READY, waiting.status)
        assertEquals(1, saved.size)
    }

    private fun member(kind: WorkforceMemberKind) = WorkforceMember(employeeCode = "PX-${kind.name.take(3)}", kind = kind)

    private fun handling(status: OperationalAssignmentStatus) = OperationalAssignment.create(
        shipmentId = UUID.randomUUID(),
        sequence = 2,
        routeLegId = null,
        type = OperationalAssignmentType.ORIGIN_HUB_HANDLING,
        requiredKind = WorkforceMemberKind.WAREHOUSE_OPERATOR,
        hubId = hubId,
        priority = 50,
        now = now.minus(Duration.ofHours(1)),
        initialStatus = if (status == OperationalAssignmentStatus.BLOCKED) status else OperationalAssignmentStatus.READY,
    )

    private fun claimBy(member: WorkforceMember, assignment: OperationalAssignment) = ShipmentAssignmentAccessRequest(
        actorUserId = member.userId,
        expectedType = OperationsAssignmentType.valueOf(assignment.type.name),
        hubId = assignment.hubId,
    )

    private fun queue(assignment: OperationalAssignment) = AssignmentDispatchQueue(
        mock(OperationalAssignmentRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findById" -> Optional.of(assignment)
                "saveAndFlush" -> invocation.arguments[0]
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        },
        mock(HubMembershipRepository::class.java),
        mock(AssignmentLockService::class.java),
        clock,
        Duration.ofMinutes(15),
    )

    private fun service(
        assignment: OperationalAssignment,
        member: WorkforceMember?,
        roles: List<String>,
        calls: MutableList<String> = mutableListOf(),
    ): OperationalAssignmentService {
        val assignments = mock(OperationalAssignmentRepository::class.java) { invocation ->
            calls += invocation.method.name
            when (invocation.method.name) {
                "findById" -> Optional.of(assignment)
                "saveAndFlush" -> invocation.arguments[0]
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
        val members = mock(WorkforceMemberRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findByUserId" -> member?.takeIf { it.userId == invocation.arguments[0] }
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
        val memberships = mock(HubMembershipRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findByWorkforceMemberIdAndHubId" -> member
                    ?.takeIf { it.id == invocation.arguments[0] && hubId == invocation.arguments[1] }
                    ?.let { HubMembership(workforceMemberId = it.id, hubId = hubId, isPrimary = true) }
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
        val identity = mock(IdentityWorkforceClient::class.java) { invocation ->
            when (invocation.method.name) {
                "snapshots" -> listOfNotNull(
                    member?.let {
                        WorkforceIdentitySnapshot(
                            userId = it.userId,
                            email = "nhanvien@pavex.com",
                            emailVerified = true,
                            accountStatus = "ACTIVE",
                            profileStatus = "ACTIVE",
                            roles = roles,
                            createdAt = now,
                            updatedAt = now,
                        )
                    },
                )
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        }
        return OperationalAssignmentService(
            assignments,
            mock(OperationalAssignmentEventLogRepository::class.java),
            members,
            memberships,
            mock(WorkforceAvailabilityRepository::class.java),
            mock(WorkforceShiftAssignmentRepository::class.java),
            mock(WorkShiftRepository::class.java),
            identity,
            mock(AssignmentLockService::class.java),
            mock(AssignmentDispatchQueue::class.java),
            clock,
        )
    }
}
