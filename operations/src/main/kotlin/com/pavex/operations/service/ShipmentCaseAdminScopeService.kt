package com.pavex.operations.service

import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ShipmentCaseAdminScopeService(
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
    private val identity: IdentityWorkforceClient,
) {
    @Transactional(readOnly = true)
    fun scope(userId: UUID): ShipmentCaseAdminScopeSnapshot {
        if (userId == ZERO_UUID) return ShipmentCaseAdminScopeSnapshot(userId)
        val identityAccessValid = identity.snapshots(listOf(userId)).singleOrNull()
            ?.let { it.accountStatus == "ACTIVE" && "HUB_MANAGER" in it.roles } == true
        if (!identityAccessValid) return ShipmentCaseAdminScopeSnapshot(userId)
        val member = members.findByUserId(userId)
            ?.takeIf { it.status == WorkforceMemberStatus.ACTIVE && it.kind == WorkforceMemberKind.DISPATCHER }
            ?: return ShipmentCaseAdminScopeSnapshot(userId)
        val hubIds = memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id)
            .map { it.hubId }
            .toSet()
        return ShipmentCaseAdminScopeSnapshot(userId, hubIds)
    }

    companion object {
        private val ZERO_UUID = UUID(0, 0)
    }
}
