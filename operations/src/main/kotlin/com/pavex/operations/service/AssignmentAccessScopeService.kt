package com.pavex.operations.service

import com.pavex.common.security.HubAccessScope
import com.pavex.operations.security.requirePavexActor
import com.pavex.operations.repository.OperationalAssignmentRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.util.UUID

typealias AssignmentAccessScope = HubAccessScope

@Service
class AssignmentAccessScopeService(
    private val hubScopes: WorkforceHubScopeResolver,
    private val assignments: OperationalAssignmentRepository,
) {
    fun resolve(authentication: Authentication): AssignmentAccessScope {
        val actor = authentication.requirePavexActor()
        if (actor.hasAuthority(ASSIGNMENTS_MANAGE)) return HubAccessScope.global(actor.userId)
        if (!actor.hasAnyAuthority(ASSIGNMENTS_READ, HUB_MANAGE)) {
            throw AccessDeniedException("Assignment access is not allowed")
        }
        return hubScopes.resolve(actor.userId)
    }

    fun canAccessAssignment(scope: AssignmentAccessScope, assignmentId: UUID): Boolean {
        val result = assignments.findById(assignmentId)
        if (result.isEmpty) return false
        return scope.canAccessHub(result.get().hubId)
    }

    companion object {
        const val ASSIGNMENTS_READ = "operations.assignments.read"
        const val ASSIGNMENTS_MANAGE = "operations.assignments.manage"
        const val HUB_MANAGE = "operations.hub.manage"
    }
}
