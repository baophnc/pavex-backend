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

class ShipmentExceptionAccessServiceTest {
    private val operations = mock(OperationsShipmentClient::class.java)
    private val service = ShipmentExceptionAccessService(operations)

    @Test
    fun `admin exception manage is global`() {
        val scope = service.resolveManage(authentication(setOf("scope.all-hubs", "shipments.exceptions.manage")))
        assertTrue(scope.isGlobal)
        verifyNoInteractions(operations)
    }

    @Test
    fun `hub manager exception manage resolves assigned hubs`() {
        val userId = UUID.randomUUID()
        val hubId = UUID.randomUUID()
        `when`(operations.caseAdminScope(userId)).thenReturn(ShipmentCaseAdminScopeSnapshot(userId, setOf(hubId)))
        val scope = service.resolveManage(
            authentication(
                setOf("ROLE_HUB_MANAGER", "operations.hub.read", "shipments.exceptions.manage"),
                userId,
            ),
        )
        assertEquals(setOf(hubId), scope.hubIds)
    }

    @Test
    fun `read capability cannot be escalated to manage`() {
        val auth = authentication(setOf("operations.hub.read", "shipments.exceptions.read"))
        assertThrows<AccessDeniedException> { service.resolveManage(auth) }
        verifyNoInteractions(operations)
    }

    private fun authentication(authorities: Set<String>, userId: UUID = UUID.randomUUID()) =
        UsernamePasswordAuthenticationToken(
            userId.toString(),
            "n/a",
            authorities.map(::SimpleGrantedAuthority),
        )
}
