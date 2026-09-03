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

class ShipmentCaseAccessServiceTest {
    private val operations = mock(OperationsShipmentClient::class.java)
    private val service = ShipmentCaseAccessService(operations)

    @Test
    fun `operations manager with case manage receives global scope`() {
        val scope = service.resolveManage(authentication(setOf("ROLE_OPERATIONS_MANAGER", "shipments.cases.manage")))
        assertTrue(scope.isGlobal)
        verifyNoInteractions(operations)
    }

    @Test
    fun `hub manager case read is scoped and cannot mutate without manage`() {
        val userId = UUID.randomUUID()
        val hubId = UUID.randomUUID()
        `when`(operations.caseAdminScope(userId)).thenReturn(ShipmentCaseAdminScopeSnapshot(userId, setOf(hubId)))
        val auth = authentication(
            setOf("ROLE_HUB_MANAGER", "operations.hub.manage", "shipments.cases.read"),
            userId,
        )

        val scope = service.resolveRead(auth)
        assertEquals(setOf(hubId), scope.hubIds)
        assertThrows<AccessDeniedException> { service.resolveManage(auth) }
    }

    @Test
    fun `hub manager case manage is limited to operations hub scope`() {
        val userId = UUID.randomUUID()
        val hubId = UUID.randomUUID()
        `when`(operations.caseAdminScope(userId)).thenReturn(ShipmentCaseAdminScopeSnapshot(userId, setOf(hubId)))
        val scope = service.resolveManage(
            authentication(
                setOf("ROLE_HUB_MANAGER", "operations.hub.manage", "shipments.cases.manage"),
                userId,
            ),
        )
        assertEquals(setOf(hubId), scope.hubIds)
        assertTrue(scope.canAccessHub(hubId))
    }

    @Test
    fun `case permission without supported system scope is rejected`() {
        assertThrows<AccessDeniedException> {
            service.resolveRead(authentication(setOf("shipments.cases.read")))
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
