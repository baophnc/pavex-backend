package com.pavex.operations.service

import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.OperationalAssignmentRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.util.UUID

data class AssignmentAccessScope(
    val isGlobal: Boolean,
    val hubIds: Set<UUID>,
) {
    fun canAccessHub(hubId: UUID): Boolean = isGlobal || hubId in hubIds
}

@Service
class AssignmentAccessScopeService(
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
    private val assignments: OperationalAssignmentRepository,
) {
    fun resolve(authentication: Authentication): AssignmentAccessScope {
        val authorities = authentication.authorities.map { it.authority }.toSet()
        if (ASSIGNMENTS_MANAGE in authorities) return AssignmentAccessScope(true, emptySet())
        if (HUB_MANAGE !in authorities) throw AccessDeniedException("Assignment access is not allowed")

        val actorUserId = runCatching { UUID.fromString(authentication.name) }
            .getOrElse { throw AccessDeniedException("Authenticated user id is invalid") }
        val member = members.findByUserId(actorUserId)
            ?: return AssignmentAccessScope(false, emptySet())
        val hubIds = memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id).map { it.hubId }.toSet()
        return AssignmentAccessScope(false, hubIds)
    }

    fun canAccessAssignment(scope: AssignmentAccessScope, assignmentId: UUID): Boolean {
        val result = assignments.findById(assignmentId)
        if (result.isEmpty) return false
        return scope.canAccessHub(result.get().hubId)
    }

    companion object {
        const val ASSIGNMENTS_MANAGE = "operations.assignments.manage"
        const val HUB_MANAGE = "operations.hub.manage"
    }
}
