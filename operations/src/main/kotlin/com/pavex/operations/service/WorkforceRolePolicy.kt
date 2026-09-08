package com.pavex.operations.service

import com.pavex.operations.dto.DispatcherAccessLevel
import com.pavex.operations.entity.WorkforceMemberKind

/**
 * Keeps the workforce kind -> Identity role contract in one place. Legacy
 * roles stay accepted during the rolling migration, but new onboarding only
 * issues the explicit role for the selected kind.
 */
object WorkforceRolePolicy {
    fun onboardingRole(kind: WorkforceMemberKind, dispatcherAccessLevel: DispatcherAccessLevel? = null): String = when (kind) {
        WorkforceMemberKind.COURIER -> "COURIER"
        WorkforceMemberKind.LINE_HAUL_DRIVER -> "LINE_HAUL_DRIVER"
        WorkforceMemberKind.WAREHOUSE_OPERATOR -> "WAREHOUSE_OPERATOR"
        WorkforceMemberKind.DISPATCHER -> when (requireNotNull(dispatcherAccessLevel) { "Dispatcher access level is required" }) {
            DispatcherAccessLevel.HUB -> "HUB_DISPATCHER"
            DispatcherAccessLevel.HUB_MANAGER -> "HUB_MANAGER"
            DispatcherAccessLevel.GLOBAL -> "OPERATIONS_MANAGER"
        }
    }

    fun isCompatible(kind: WorkforceMemberKind, roles: Collection<String>): Boolean =
        roles.any { it in acceptedRoles(kind) }

    fun acceptedRoles(kind: WorkforceMemberKind): Set<String> = when (kind) {
        WorkforceMemberKind.WAREHOUSE_OPERATOR -> setOf("WAREHOUSE_OPERATOR", "WAREHOUSE_STAFF")
        WorkforceMemberKind.COURIER -> setOf("COURIER", "DRIVER")
        WorkforceMemberKind.LINE_HAUL_DRIVER -> setOf("LINE_HAUL_DRIVER", "DRIVER")
        WorkforceMemberKind.DISPATCHER -> setOf(
            "HUB_DISPATCHER",
            "OPERATIONS_MANAGER",
            "HUB_MANAGER",
        )
    }
}
