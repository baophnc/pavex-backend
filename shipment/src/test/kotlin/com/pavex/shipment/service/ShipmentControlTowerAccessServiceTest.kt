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
    fun `control tower permission grants global scope`() {
        val auth = authentication(setOf("shipments.control-tower.read"))
        val scope = service.resolve(auth)
        assertTrue(scope.global)
        verifyNoInteractions(operations)
    }

    @Test
    fun `hub manager receives operations-backed hub scope`() {
        val userId = UUID.randomUUID()
        val hubId = UUID.randomUUID()
        `when`(operations.caseAdminScope(userId)).thenReturn(ShipmentCaseAdminScopeSnapshot(userId, setOf(hubId)))
        val auth = authentication(setOf("ROLE_HUB_MANAGER", "operations.hub.manage"), userId)
        val scope = service.resolve(auth)
        assertEquals(setOf(hubId), scope.hubIds)
        assertTrue(scope.canAccessHub(hubId))
    }

    @Test
    fun `unprivileged user is rejected`() {
        assertThrows<AccessDeniedException> { service.resolve(authentication(emptySet())) }
    }

    @Test
    fun `malformed user subject is rejected before operations lookup`() {
        val auth = UsernamePasswordAuthenticationToken(
            "not-a-uuid",
            "n/a",
            listOf(SimpleGrantedAuthority("operations.hub.manage"), SimpleGrantedAuthority("ROLE_HUB_MANAGER")),
        )
        assertThrows<AccessDeniedException> { service.resolve(auth) }
        verifyNoInteractions(operations)
    }

    private fun authentication(authorities: Set<String>, userId: UUID = UUID.randomUUID()) =
        UsernamePasswordAuthenticationToken(
            userId.toString(),
            "n/a",
            authorities.map(::SimpleGrantedAuthority),
        )
}
