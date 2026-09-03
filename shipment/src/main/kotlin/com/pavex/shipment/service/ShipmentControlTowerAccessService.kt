package com.pavex.shipment.service

import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.security.requirePavexActor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

typealias ShipmentControlTowerScope = HubAccessScope

@Service
class ShipmentControlTowerAccessService(
    private val operations: OperationsShipmentClient,
) {
    fun resolve(authentication: Authentication): ShipmentControlTowerScope {
        val actor = authentication.requirePavexActor()
        if (!actor.hasAuthority(CONTROL_TOWER_READ)) {
            throw AccessDeniedException("Shipment control tower access is not allowed.")
        }
        if (actor.hasRole(ADMIN_ROLE) || actor.hasRole(OPERATIONS_MANAGER_ROLE)) {
            return HubAccessScope.global(actor.userId)
        }
        if (!actor.hasAuthority(HUB_MANAGE) || !actor.hasRole(HUB_MANAGER_ROLE)) {
            throw AccessDeniedException("Shipment control tower access is not allowed.")
        }
        val scope = operations.caseAdminScope(actor.userId)
        if (scope.userId != actor.userId || scope.hubIds.isEmpty()) {
            throw AccessDeniedException("Shipment control tower hub scope is empty.")
        }
        return HubAccessScope.assignedHubs(actor.userId, scope.hubIds)
    }

    companion object {
        const val CONTROL_TOWER_READ = "shipments.control-tower.read"
        const val HUB_MANAGE = "operations.hub.manage"
        const val ADMIN_ROLE = "ADMIN"
        const val OPERATIONS_MANAGER_ROLE = "OPERATIONS_MANAGER"
        const val HUB_MANAGER_ROLE = "HUB_MANAGER"
    }
}
