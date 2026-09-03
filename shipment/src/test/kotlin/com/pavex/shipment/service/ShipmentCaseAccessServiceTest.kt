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
    fun `case manager receives global scope`() {
        val scope = service.resolve(authentication(setOf("shipments.cases.manage")))
        assertTrue(scope.isGlobal)
        verifyNoInteractions(operations)
    }

    @Test
    fun `hub manager with scoped case read receives operations hub scope`() {
        val userId = UUID.randomUUID()
        val hubId = UUID.randomUUID()
        `when`(operations.caseAdminScope(userId)).thenReturn(ShipmentCaseAdminScopeSnapshot(userId, setOf(hubId)))

        val scope = service.resolve(
            authentication(
                setOf("ROLE_HUB_MANAGER", "operations.hub.manage", "shipments.cases.read"),
                userId,
            ),
        )

        assertEquals(setOf(hubId), scope.hubIds)
        assertTrue(scope.canAccessHub(hubId))
    }

    @Test
    fun `hub role without case read permission is rejected`() {
        assertThrows<AccessDeniedException> {
            service.resolve(authentication(setOf("ROLE_HUB_MANAGER", "operations.hub.manage")))
        }
        verifyNoInteractions(operations)
    }

    @Test
    fun `empty operations hub scope is rejected`() {
        val userId = UUID.randomUUID()
        `when`(operations.caseAdminScope(userId)).thenReturn(ShipmentCaseAdminScopeSnapshot(userId, emptySet()))

        assertThrows<AccessDeniedException> {
            service.resolve(
                authentication(
                    setOf("ROLE_HUB_MANAGER", "operations.hub.manage", "shipments.cases.read"),
                    userId,
                ),
            )
        }
    }

    private fun authentication(authorities: Set<String>, userId: UUID = UUID.randomUUID()) =
        UsernamePasswordAuthenticationToken(
            userId.toString(),
            "n/a",
            authorities.map(::SimpleGrantedAuthority),
        )
}
