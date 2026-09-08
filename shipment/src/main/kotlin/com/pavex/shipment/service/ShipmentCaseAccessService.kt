package com.pavex.shipment.service

import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.security.requirePavexActor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

typealias ShipmentCaseAccessScope = HubAccessScope

@Service
class ShipmentCaseAccessService(
    private val operations: OperationsShipmentClient,
) {
    fun resolveRead(authentication: Authentication): ShipmentCaseAccessScope =
        resolve(authentication, CASES_READ, allowGlobalManageFallback = true)

    fun resolveManage(authentication: Authentication): ShipmentCaseAccessScope =
        resolve(authentication, CASES_MANAGE, allowGlobalManageFallback = false)

    private fun resolve(
        authentication: Authentication,
        requiredAuthority: String,
        allowGlobalManageFallback: Boolean,
    ): ShipmentCaseAccessScope {
        val actor = authentication.requirePavexActor()
        val hasRequired = actor.hasAuthority(requiredAuthority) ||
            (allowGlobalManageFallback && actor.hasAuthority(CASES_MANAGE))
        if (!hasRequired) throw AccessDeniedException("Shipment case access is not allowed.")

        if (actor.hasAnyRole(ADMIN_ROLE, PLATFORM_SUPER_ADMIN_ROLE, OPERATIONS_MANAGER_ROLE)) {
            return HubAccessScope.global(actor.userId)
        }
        val scopedRoleAllowed = if (requiredAuthority == CASES_MANAGE) {
            actor.hasRole(HUB_MANAGER_ROLE) && actor.hasAuthority(HUB_MANAGE)
        } else {
            actor.hasAnyRole(HUB_MANAGER_ROLE, HUB_DISPATCHER_ROLE) &&
                (actor.hasAuthority(HUB_READ) || actor.hasAuthority(HUB_MANAGE))
        }
        if (!scopedRoleAllowed) {
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
        const val HUB_READ = "operations.hub.read"
        const val HUB_MANAGE = "operations.hub.manage"
        const val ADMIN_ROLE = "ADMIN"
        const val PLATFORM_SUPER_ADMIN_ROLE = "PLATFORM_SUPER_ADMIN"
        const val OPERATIONS_MANAGER_ROLE = "OPERATIONS_MANAGER"
        const val HUB_MANAGER_ROLE = "HUB_MANAGER"
        const val HUB_DISPATCHER_ROLE = "HUB_DISPATCHER"
    }
}
