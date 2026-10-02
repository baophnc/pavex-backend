package com.pavex.shipment.service

import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.security.requirePavexActor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

typealias ShipmentExceptionAccessScope = HubAccessScope

@Service
class ShipmentExceptionAccessService(
    private val operations: OperationsShipmentClient,
) {
    fun resolveRead(authentication: Authentication): ShipmentExceptionAccessScope =
        resolve(authentication, setOf(EXCEPTIONS_READ, EXCEPTIONS_MANAGE))

    /** Asking for a return, forward or cancellation: hub staff may, and someone else approves. */
    fun resolveRequest(authentication: Authentication): ShipmentExceptionAccessScope =
        resolve(authentication, setOf(EXCEPTIONS_REQUEST, EXCEPTIONS_MANAGE))

    fun resolveManage(authentication: Authentication): ShipmentExceptionAccessScope =
        resolve(authentication, setOf(EXCEPTIONS_MANAGE))

    fun resolveWeightAdjust(authentication: Authentication): ShipmentExceptionAccessScope =
        resolve(authentication, setOf(WEIGHT_ADJUST))

    private fun resolve(authentication: Authentication, acceptedAuthorities: Set<String>): ShipmentExceptionAccessScope {
        val actor = authentication.requirePavexActor()
        if (acceptedAuthorities.none(actor::hasAuthority)) {
            throw AccessDeniedException("Shipment exception access is not allowed.")
        }
        if (actor.seesAllHubs) return HubAccessScope.global(actor.userId)
        if (!actor.hasAnyAuthority(HUB_READ, HUB_MANAGE)) {
            throw AccessDeniedException("Shipment exception access is not allowed.")
        }
        val scope = operations.caseAdminScope(actor.userId)
        if (scope.userId != actor.userId || scope.hubIds.isEmpty()) {
            throw AccessDeniedException("Shipment exception hub scope is empty.")
        }
        return HubAccessScope.assignedHubs(actor.userId, scope.hubIds)
    }

    companion object {
        const val EXCEPTIONS_READ = "shipments.exceptions.read"
        const val EXCEPTIONS_MANAGE = "shipments.exceptions.manage"
        const val EXCEPTIONS_REQUEST = "shipments.exceptions.request"
        const val WEIGHT_ADJUST = "shipments.weight.adjust"
        const val HUB_READ = "operations.hub.read"
        const val HUB_MANAGE = "operations.hub.manage"
    }
}
