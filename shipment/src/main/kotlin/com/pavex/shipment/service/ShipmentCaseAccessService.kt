package com.pavex.shipment.service

import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.security.requirePavexActor
import com.pavex.shipment.client.OperationsShipmentClient
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

typealias ShipmentCaseAccessScope = HubAccessScope

@Service
class ShipmentCaseAccessService(
    private val operations: OperationsShipmentClient,
) {
    fun resolve(authentication: Authentication): ShipmentCaseAccessScope {
        val actor = authentication.requirePavexActor()
        if (actor.hasAuthority(CASES_MANAGE)) return HubAccessScope.global(actor.userId)
        if (!actor.hasAuthority(CASES_READ) || !actor.hasAuthority(HUB_MANAGE) || !actor.hasRole(HUB_MANAGER_ROLE)) {
            throw AccessDeniedException("Shipment case access is not allowed.")
        }
        val scope = operations.caseAdminScope(actor.userId)
        if (scope.userId != actor.userId || scope.hubIds.isEmpty()) {
            throw AccessDeniedException("Shipment case hub scope is empty.")
        }
        return HubAccessScope.assignedHubs(actor.userId, scope.hubIds)
    }

    companion object {
        const val CASES_READ = "shipments.cases.read"
        const val CASES_MANAGE = "shipments.cases.manage"
        const val HUB_MANAGE = "operations.hub.manage"
        const val HUB_MANAGER_ROLE = "HUB_MANAGER"
    }
}
