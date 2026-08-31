package com.pavex.operations.service

import com.pavex.operations.entity.HubMembership
import com.pavex.operations.entity.OperationalAssignment
import com.pavex.operations.entity.OperationalAssignmentType
import com.pavex.operations.entity.WorkforceMember
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.OperationalAssignmentRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AssignmentAccessScopeServiceTest {
    private val members = mock(WorkforceMemberRepository::class.java)
    private val memberships = mock(HubMembershipRepository::class.java)
    private val assignments = mock(OperationalAssignmentRepository::class.java)
    private val service = AssignmentAccessScopeService(members, memberships, assignments)

    @Test
    fun `global assignment manager receives global scope`() {
        val authentication = auth("operations.assignments.manage")
        assertTrue(service.resolve(authentication).isGlobal)
    }

    @Test
    fun `hub manager can only access assignment at owned hub`() {
        val userId = UUID.randomUUID()
        val member = WorkforceMember.create(userId, WorkforceMemberKind.DISPATCHER, Instant.now())
        val ownedHub = UUID.randomUUID()
        val foreignHub = UUID.randomUUID()
        `when`(members.findByUserId(userId)).thenReturn(member)
        `when`(memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id)).thenReturn(
            listOf(HubMembership(workforceMemberId = member.id, hubId = ownedHub)),
        )
        val ownedAssignment = assignment(ownedHub)
        val foreignAssignment = assignment(foreignHub)
        `when`(assignments.findById(ownedAssignment.id)).thenReturn(Optional.of(ownedAssignment))
        `when`(assignments.findById(foreignAssignment.id)).thenReturn(Optional.of(foreignAssignment))

        val scope = service.resolve(auth("operations.hub.manage", userId))

        assertFalse(scope.isGlobal)
        assertTrue(service.canAccessAssignment(scope, ownedAssignment.id))
        assertFalse(service.canAccessAssignment(scope, foreignAssignment.id))
    }

    private fun auth(authority: String, userId: UUID = UUID.randomUUID()) =
        UsernamePasswordAuthenticationToken(
            userId.toString(),
            "n/a",
            listOf(SimpleGrantedAuthority(authority)),
        )

    private fun assignment(hubId: UUID) = OperationalAssignment.create(
        shipmentId = UUID.randomUUID(),
        sequence = 1,
        routeLegId = null,
        type = OperationalAssignmentType.PICKUP,
        requiredKind = WorkforceMemberKind.COURIER,
        hubId = hubId,
        priority = 50,
        now = Instant.now(),
    )
}
