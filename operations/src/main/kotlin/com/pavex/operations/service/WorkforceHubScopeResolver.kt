package com.pavex.operations.service

import com.pavex.common.security.HubAccessScope
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkforceHubScopeResolver(
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
) {
    @Transactional(readOnly = true)
    fun resolve(userId: UUID, requiredKind: WorkforceMemberKind? = null): HubAccessScope {
        val member = members.findByUserId(userId)
            ?.takeIf { it.status == WorkforceMemberStatus.ACTIVE }
            ?.takeIf { requiredKind == null || it.kind == requiredKind }
            ?: return HubAccessScope.assignedHubs(userId, emptySet())
        val hubIds = memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id)
            .map { it.hubId }
            .toSet()
        return HubAccessScope.assignedHubs(userId, hubIds)
    }
}
