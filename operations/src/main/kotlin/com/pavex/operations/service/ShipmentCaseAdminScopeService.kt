package com.pavex.operations.service

import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.entity.WorkforceMemberKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ShipmentCaseAdminScopeService(
    private val identity: IdentityWorkforceClient,
    private val hubScopes: WorkforceHubScopeResolver,
) {
    @Transactional(readOnly = true)
    fun scope(userId: UUID): ShipmentCaseAdminScopeSnapshot {
        if (userId == ZERO_UUID) return ShipmentCaseAdminScopeSnapshot(userId)
        val identityAccessValid = identity.snapshots(listOf(userId)).singleOrNull()
            ?.let { it.accountStatus == "ACTIVE" && "HUB_MANAGER" in it.roles } == true
        if (!identityAccessValid) return ShipmentCaseAdminScopeSnapshot(userId)
        val scope = hubScopes.resolve(userId, WorkforceMemberKind.DISPATCHER)
        return ShipmentCaseAdminScopeSnapshot(userId, scope.hubIds)
    }

    companion object {
        private val ZERO_UUID = UUID(0, 0)
    }
}
