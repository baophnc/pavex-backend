package com.pavex.operations.service

import com.pavex.operations.dto.DispatcherAccessLevel
import com.pavex.operations.entity.WorkforceMemberKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkforceRolePolicyTest {
    @Test
    fun `courier and line haul identities are no longer interchangeable`() {
        assertTrue(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.COURIER, setOf("COURIER")))
        assertTrue(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.LINE_HAUL_DRIVER, setOf("LINE_HAUL_DRIVER")))
        assertFalse(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.COURIER, setOf("LINE_HAUL_DRIVER")))
        assertFalse(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.LINE_HAUL_DRIVER, setOf("COURIER")))
    }

    @Test
    fun `legacy roles stay compatible during migration`() {
        assertTrue(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.COURIER, setOf("DRIVER")))
        assertTrue(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.LINE_HAUL_DRIVER, setOf("DRIVER")))
        assertTrue(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.WAREHOUSE_OPERATOR, setOf("WAREHOUSE_STAFF")))
    }

    @Test
    fun `dispatcher onboarding maps hub manager access to hub manager role`() {
        assertEquals(
            "HUB_MANAGER",
            WorkforceRolePolicy.onboardingRole(WorkforceMemberKind.DISPATCHER, DispatcherAccessLevel.HUB_MANAGER),
        )
    }

    @Test
    fun `dispatcher onboarding keeps hub and global access mappings`() {
        assertEquals(
            "HUB_DISPATCHER",
            WorkforceRolePolicy.onboardingRole(WorkforceMemberKind.DISPATCHER, DispatcherAccessLevel.HUB),
        )
        assertEquals(
            "OPERATIONS_MANAGER",
            WorkforceRolePolicy.onboardingRole(WorkforceMemberKind.DISPATCHER, DispatcherAccessLevel.GLOBAL),
        )
    }
}
