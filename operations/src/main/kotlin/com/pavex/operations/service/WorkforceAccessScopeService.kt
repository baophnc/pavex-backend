package com.pavex.operations.service

import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.util.UUID

data class WorkforceAccessScope(
    val isGlobal: Boolean,
    val hubIds: Set<UUID>,
) {
    fun canAccessHub(hubId: UUID): Boolean = isGlobal || hubId in hubIds
}

@Service
class WorkforceAccessScopeService(
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
) {
    fun resolve(authentication: Authentication): WorkforceAccessScope {
        val authorities = authentication.authorities.map { it.authority }.toSet()
        if (WORKFORCE_MANAGE in authorities) {
            return WorkforceAccessScope(isGlobal = true, hubIds = emptySet())
        }
        if (HUB_MANAGE !in authorities) throw AccessDeniedException("Workforce access is not allowed")

        val actorUserId = runCatching { UUID.fromString(authentication.name) }
            .getOrElse { throw AccessDeniedException("Authenticated user id is invalid") }
        val member = members.findByUserId(actorUserId)
            ?: return WorkforceAccessScope(isGlobal = false, hubIds = emptySet())
        val hubIds = memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id)
            .map { it.hubId }
            .toSet()
        return WorkforceAccessScope(isGlobal = false, hubIds = hubIds)
    }

    companion object {
        const val WORKFORCE_MANAGE = "operations.workforce.manage"
        const val HUB_MANAGE = "operations.hub.manage"
    }
}
