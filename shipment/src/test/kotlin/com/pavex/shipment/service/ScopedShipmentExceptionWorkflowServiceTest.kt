package com.pavex.shipment.service

import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.entity.ShipmentExceptionRequest
import com.pavex.shipment.entity.ShipmentExceptionRequestKind
import com.pavex.shipment.exception.ShipmentForbiddenException
import com.pavex.shipment.repository.ShipmentExceptionRequestRepository
import com.pavex.shipment.repository.ShipmentRepository
import com.pavex.shipment.repository.ShipmentWeightAdjustmentRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.Instant
import java.util.UUID

class ScopedShipmentExceptionWorkflowServiceTest {
    private val workflow = mock(ShipmentExceptionWorkflowService::class.java)
    private val shipments = mock(ShipmentRepository::class.java)
    private val requests = mock(ShipmentExceptionRequestRepository::class.java)
    private val adjustments = mock(ShipmentWeightAdjustmentRepository::class.java)
    private val operations = mock(OperationsShipmentClient::class.java)
    private val access = ShipmentExceptionAccessService(operations)
    private val service = ScopedShipmentExceptionWorkflowService(workflow, shipments, requests, adjustments, access)

    @Test
    fun `hub manager cannot review request owned by another hub`() {
        val userId = UUID.randomUUID()
        val allowedHubId = UUID.randomUUID()
        val foreignHubId = UUID.randomUUID()
        val shipmentId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        `when`(operations.caseAdminScope(userId)).thenReturn(
            ShipmentCaseAdminScopeSnapshot(userId, setOf(allowedHubId)),
        )
        `when`(requests.findByIdAndShipmentId(requestId, shipmentId)).thenReturn(
            ShipmentExceptionRequest(
                id = requestId,
                shipmentId = shipmentId,
                kind = ShipmentExceptionRequestKind.RETURN,
                reason = "Damaged parcel",
                hubId = foreignHubId,
                requestedByUserId = UUID.randomUUID(),
                requestedAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

        assertThrows<ShipmentForbiddenException> {
            service.review(authentication(userId), shipmentId, requestId, true, "approved")
        }
        verifyNoInteractions(workflow)
    }

    private fun authentication(userId: UUID) = UsernamePasswordAuthenticationToken(
        userId.toString(),
        "n/a",
        listOf(
            SimpleGrantedAuthority("ROLE_HUB_MANAGER"),
            SimpleGrantedAuthority("operations.hub.read"),
            SimpleGrantedAuthority("shipments.exceptions.manage"),
        ),
    )
}
