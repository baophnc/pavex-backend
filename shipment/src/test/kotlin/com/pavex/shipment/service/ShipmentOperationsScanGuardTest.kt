package com.pavex.shipment.service

import com.pavex.contracts.network.ShipmentOperationalHubSnapshot
import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.OperationsCapability
import com.pavex.contracts.operations.ShipmentOperationsAssignmentSnapshot
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.RecordParcelScanRequest
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

class ShipmentOperationsScanGuardTest {
    private val operations = mock(OperationsShipmentClient::class.java)
    private val guard = ShipmentOperationsScanGuard(operations)
    private val actorUserId = UUID.randomUUID()
    private val shipmentId = UUID.randomUUID()
    private val originHubId = UUID.randomUUID()
    private val transitHubId = UUID.randomUUID()
    private val destinationHubId = UUID.randomUUID()

    @Test
    fun `origin receipt completes the active origin handling assignment`() {
        val target = target(ParcelStatus.PICKED_UP, null)
        val hub = hub(originHubId)
        val handling = assignment(OperationsAssignmentType.ORIGIN_HUB_HANDLING, originHubId)
        allowScanning(hub)
        `when`(operations.findTypeAssignment(shipmentId, OperationsAssignmentType.ORIGIN_HUB_HANDLING, originHubId))
            .thenReturn(handling)

        val context = guard.validate(
            actorUserId,
            target,
            hub,
            RecordParcelScanRequest(target.parcelCode, originHubId, ParcelStatus.AT_ORIGIN_HUB),
        )

        assertEquals(handling, context.handlingAssignment)
    }

    @Test
    fun `transit arrival completes line haul without requiring blocked handling`() {
        val routeLegId = UUID.randomUUID()
        val target = target(ParcelStatus.IN_TRANSIT, null)
        val hub = hub(transitHubId)
        val lineHaul = assignment(OperationsAssignmentType.LINE_HAUL, originHubId, routeLegId)
        allowScanning(hub)
        `when`(operations.findRouteLegAssignment(shipmentId, routeLegId)).thenReturn(lineHaul)

        val context = guard.validate(
            actorUserId,
            target,
            hub,
            RecordParcelScanRequest(target.parcelCode, transitHubId, ParcelStatus.AT_TRANSIT_HUB, routeLegId = routeLegId),
        )

        assertEquals(lineHaul, context.arrivingLineHaul)
        assertNull(context.handlingAssignment)
        verify(operations, never()).findTypeAssignment(
            shipmentId,
            OperationsAssignmentType.TRANSIT_HUB_HANDLING,
            transitHubId,
        )
    }

    @Test
    fun `transit checkpoint completes handling before the next line haul`() {
        val target = target(ParcelStatus.AT_TRANSIT_HUB, transitHubId)
        val hub = hub(transitHubId)
        val handling = assignment(OperationsAssignmentType.TRANSIT_HUB_HANDLING, transitHubId)
        allowScanning(hub)
        `when`(operations.findTypeAssignment(shipmentId, OperationsAssignmentType.TRANSIT_HUB_HANDLING, transitHubId))
            .thenReturn(handling)

        val context = guard.validate(
            actorUserId,
            target,
            hub,
            RecordParcelScanRequest(target.parcelCode, transitHubId),
        )

        assertEquals(handling, context.handlingAssignment)
    }

    @Test
    fun `checkpoint at a hub other than the parcel location is rejected`() {
        val target = target(ParcelStatus.AT_TRANSIT_HUB, transitHubId)
        val wrongHub = hub(destinationHubId)

        assertThrows<InvalidShipmentRequestException> {
            guard.validate(
                actorUserId,
                target,
                wrongHub,
                RecordParcelScanRequest(target.parcelCode, destinationHubId),
            )
        }
    }

    private fun allowScanning(hub: ShipmentOperationalHubSnapshot) {
        `when`(operations.canOperateAtHub(actorUserId, hub.hubId, OperationsCapability.PARCEL_SCANNING))
            .thenReturn(true)
    }

    private fun target(status: ParcelStatus, currentHubId: UUID?) = ParcelScanTarget(
        shipmentId = shipmentId,
        parcelId = UUID.randomUUID(),
        parcelCode = "PAVEX-P01",
        parcelStatus = status,
        currentHubId = currentHubId,
        routeRevision = 1,
        originHubId = originHubId,
        destinationHubId = destinationHubId,
        lifecycleStatus = ShipmentLifecycleStatus.ACTIVE,
    )

    private fun hub(id: UUID) = ShipmentOperationalHubSnapshot(id, "HUB", "Hub", true, true)

    private fun assignment(type: OperationsAssignmentType, hubId: UUID, routeLegId: UUID? = null) =
        ShipmentOperationsAssignmentSnapshot(
            id = UUID.randomUUID(),
            shipmentId = shipmentId,
            sequence = 1,
            routeLegId = routeLegId,
            type = type,
            hubId = hubId,
            assigneeWorkforceMemberId = UUID.randomUUID(),
            status = OperationsAssignmentStatus.IN_PROGRESS,
        )
}
