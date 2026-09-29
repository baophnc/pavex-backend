package com.pavex.operations.service

import com.pavex.operations.entity.WorkforceMemberKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkforceRolePolicyTest {
    @Test
    fun `courier and line haul identities are not interchangeable`() {
        assertTrue(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.COURIER, setOf("COURIER")))
        assertTrue(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.LINE_HAUL_DRIVER, setOf("LINE_HAUL_DRIVER")))
        assertFalse(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.COURIER, setOf("LINE_HAUL_DRIVER")))
        assertFalse(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.LINE_HAUL_DRIVER, setOf("COURIER")))
    }

    @Test
    fun `each kind is onboarded with the role of the same name`() {
        WorkforceMemberKind.entries.forEach { kind ->
            assertEquals(kind.name, WorkforceRolePolicy.onboardingRole(kind))
        }
    }

    @Test
    fun `a head office role does not make someone a hub manager`() {
        assertFalse(WorkforceRolePolicy.isCompatible(WorkforceMemberKind.HUB_MANAGER, setOf("OPERATIONS_MANAGER")))
    }
}
