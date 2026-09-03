package com.pavex.shipment.service

import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.shipment.client.OperationsShipmentClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

class ShipmentControlTowerAccessServiceTest {
    private val operations = mock(OperationsShipmentClient::class.java)
    private val service = ShipmentControlTowerAccessService(operations)

    @Test
    fun `operations manager control tower permission grants global scope`() {
        val auth = authentication(setOf("ROLE_OPERATIONS_MANAGER", "shipments.control-tower.read"))
        val scope = service.resolve(auth)
        assertTrue(scope.global)
        verifyNoInteractions(operations)
    }

    @Test
    fun `hub manager control tower read receives operations-backed hub scope`() {
        val userId = UUID.randomUUID()
        val hubId = UUID.randomUUID()
        `when`(operations.caseAdminScope(userId)).thenReturn(ShipmentCaseAdminScopeSnapshot(userId, setOf(hubId)))
        val auth = authentication(
            setOf("ROLE_HUB_MANAGER", "operations.hub.manage", "shipments.control-tower.read"),
            userId,
        )
        val scope = service.resolve(auth)
        assertEquals(setOf(hubId), scope.hubIds)
        assertTrue(scope.canAccessHub(hubId))
    }

    @Test
    fun `hub manager without control tower capability is rejected before lookup`() {
        assertThrows<AccessDeniedException> {
            service.resolve(authentication(setOf("ROLE_HUB_MANAGER", "operations.hub.manage")))
        }
        verifyNoInteractions(operations)
    }

    private fun authentication(authorities: Set<String>, userId: UUID = UUID.randomUUID()) =
        UsernamePasswordAuthenticationToken(
            userId.toString(),
            "n/a",
            authorities.map(::SimpleGrantedAuthority),
        )
}
