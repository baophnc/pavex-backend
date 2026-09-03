package com.pavex.operations.service

import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.operations.client.IdentityWorkforceClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Internal workforce hub-scope lookup used by shipment-service.
 *
 * Authorization capability stays in shipment-service (JWT permissions). This
 * service only answers the data-scope question for an active identity + active
 * workforce member, so it can safely support HUB_MANAGER and other scoped
 * workforce roles without duplicating the RBAC catalog here.
 */
@Service
class ShipmentCaseAdminScopeService(
    private val identity: IdentityWorkforceClient,
    private val hubScopes: WorkforceHubScopeResolver,
) {
    @Transactional(readOnly = true)
    fun scope(userId: UUID): ShipmentCaseAdminScopeSnapshot {
        if (userId == ZERO_UUID) return ShipmentCaseAdminScopeSnapshot(userId)
        val identityActive = identity.snapshots(listOf(userId)).singleOrNull()?.accountStatus == "ACTIVE"
        if (!identityActive) return ShipmentCaseAdminScopeSnapshot(userId)
        val scope = hubScopes.resolve(userId)
        return ShipmentCaseAdminScopeSnapshot(userId, scope.hubIds)
    }

    companion object {
        private val ZERO_UUID = UUID(0, 0)
    }
}
