package com.pavex.shipment.service

import com.pavex.shipment.client.OperationsShipmentClient
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.util.UUID

data class ShipmentControlTowerScope(
    val global: Boolean,
    val hubIds: Set<UUID>,
) {
    fun canAccessHub(hubId: UUID): Boolean = global || hubId in hubIds
}

@Service
class ShipmentControlTowerAccessService(
    private val operations: OperationsShipmentClient,
) {
    fun resolve(authentication: Authentication): ShipmentControlTowerScope {
        val authorities = authentication.authorities.map { it.authority }.toSet()
        if (CONTROL_TOWER_READ in authorities) return ShipmentControlTowerScope(true, emptySet())
        if (HUB_MANAGE !in authorities || HUB_MANAGER !in authorities) {
            throw AccessDeniedException("Shipment control tower access is not allowed.")
        }
        val userId = runCatching { UUID.fromString(authentication.name) }
            .getOrElse { throw AccessDeniedException("Authenticated user id is invalid.") }
        val scope = operations.caseAdminScope(userId)
        if (scope.userId != userId || scope.hubIds.isEmpty()) {
            throw AccessDeniedException("Shipment control tower hub scope is empty.")
        }
        return ShipmentControlTowerScope(false, scope.hubIds)
    }

    companion object {
        const val CONTROL_TOWER_READ = "shipments.control-tower.read"
        const val HUB_MANAGE = "operations.hub.manage"
        const val HUB_MANAGER = "ROLE_HUB_MANAGER"
    }
}
