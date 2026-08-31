package com.pavex.shipment.service

import com.pavex.shipment.client.OperationsShipmentClient
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.util.UUID

data class ShipmentCaseAccessScope(
    val isGlobal: Boolean,
    val hubIds: Set<UUID>,
) {
    fun canAccessHub(hubId: UUID): Boolean = isGlobal || hubId in hubIds
}

@Service
class ShipmentCaseAccessService(
    private val operations: OperationsShipmentClient,
) {
    fun resolve(authentication: Authentication): ShipmentCaseAccessScope {
        val authorities = authentication.authorities.map { it.authority }.toSet()
        if (CASES_MANAGE in authorities) return ShipmentCaseAccessScope(true, emptySet())
        if (HUB_MANAGE !in authorities || HUB_MANAGER !in authorities) {
            throw AccessDeniedException("Shipment case access is not allowed.")
        }
        val actorUserId = runCatching { UUID.fromString(authentication.name) }
            .getOrElse { throw AccessDeniedException("Authenticated user id is invalid.") }
        val scope = operations.caseAdminScope(actorUserId)
        if (scope.userId != actorUserId || scope.hubIds.isEmpty()) {
            throw AccessDeniedException("Shipment case hub scope is empty.")
        }
        return ShipmentCaseAccessScope(false, scope.hubIds)
    }

    companion object {
        const val CASES_MANAGE = "shipments.cases.manage"
        const val HUB_MANAGE = "operations.hub.manage"
        const val HUB_MANAGER = "ROLE_HUB_MANAGER"
    }
}
