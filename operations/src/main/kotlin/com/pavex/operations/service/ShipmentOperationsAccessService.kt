package com.pavex.operations.service

import com.pavex.contracts.operations.OperationsCapability
import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ShipmentOperationsAccessService(
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
    private val identity: IdentityWorkforceClient,
) {
    @Transactional(readOnly = true)
    fun canOperateAtHub(userId: UUID, hubId: UUID, capability: OperationsCapability): Boolean {
        if (userId == ZERO_UUID || hubId == ZERO_UUID) return false
        val allowedKinds = allowedKinds(capability)
        val allowedRoles = allowedRoles(capability)
        val member = members.findByUserId(userId)
            ?.takeIf { it.status == WorkforceMemberStatus.ACTIVE && it.kind in allowedKinds }
            ?: return false
        if (memberships.findByWorkforceMemberIdAndHubId(member.id, hubId)?.isActive != true) return false
        val snapshot = identity.snapshots(listOf(userId)).singleOrNull() ?: return false
        if (snapshot.accountStatus != "ACTIVE") return false
        return snapshot.roles.any { it in allowedRoles }
    }

    private fun allowedKinds(capability: OperationsCapability): Set<WorkforceMemberKind> = when (capability) {
        OperationsCapability.PARCEL_SCANNING -> HUB_STAFF_KINDS
        OperationsCapability.PICKUP, OperationsCapability.LAST_MILE_DELIVERY -> setOf(WorkforceMemberKind.COURIER)
        OperationsCapability.LINE_HAUL -> setOf(WorkforceMemberKind.LINE_HAUL_DRIVER)
        OperationsCapability.CASH_HANDLING -> HUB_STAFF_KINDS
    }

    private fun allowedRoles(capability: OperationsCapability): Set<String> = when (capability) {
        OperationsCapability.PARCEL_SCANNING,
        OperationsCapability.CASH_HANDLING,
        -> HUB_STAFF_KINDS.flatMapTo(mutableSetOf(), WorkforceRolePolicy::acceptedRoles)
        OperationsCapability.PICKUP,
        OperationsCapability.LAST_MILE_DELIVERY,
        -> WorkforceRolePolicy.acceptedRoles(WorkforceMemberKind.COURIER)
        OperationsCapability.LINE_HAUL -> WorkforceRolePolicy.acceptedRoles(WorkforceMemberKind.LINE_HAUL_DRIVER)
    }

    companion object {
        private val ZERO_UUID = UUID(0, 0)
        /** Hub managers work the counter and the sorting line alongside their staff. */
        private val HUB_STAFF_KINDS = setOf(WorkforceMemberKind.WAREHOUSE_OPERATOR, WorkforceMemberKind.HUB_MANAGER)
    }
}
