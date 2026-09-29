package com.pavex.shipment.service

import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.security.requirePavexActor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

/**
 * Head office reads every shipment; hub staff read the shipments that start, end,
 * currently sit, or have been handled at one of their hubs.
 */
@Service
class AdminShipmentAccessService(
    private val operations: OperationsShipmentClient,
) {
    fun resolve(authentication: Authentication): HubAccessScope {
        val actor = authentication.requirePavexActor()
        if (actor.hasAuthority(ADMIN_READ) && actor.seesAllHubs) return HubAccessScope.global(actor.userId)
        if (!actor.hasAnyAuthority(ADMIN_READ, HUB_READ)) {
            throw AccessDeniedException("Shipment access is not allowed.")
        }
        val scope = operations.caseAdminScope(actor.userId)
        if (scope.userId != actor.userId || scope.hubIds.isEmpty()) {
            throw AccessDeniedException("Shipment hub scope is empty.")
        }
        return HubAccessScope.assignedHubs(actor.userId, scope.hubIds)
    }

    companion object {
        const val ADMIN_READ = "shipments.admin.read"
        const val HUB_READ = "shipments.read-hub"
    }
}
