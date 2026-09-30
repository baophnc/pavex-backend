package com.pavex.shipment.service

import com.pavex.common.security.PavexActor
import com.pavex.contracts.network.ShipmentOperationalHubSnapshot
import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.RecordParcelScanRequest
import com.pavex.shipment.dto.WorkforceScanAction
import com.pavex.shipment.dto.WorkforceScanRequest
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentFixtures
import com.pavex.shipment.entity.ShippingFeePayer
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.ParcelRepository
import com.pavex.shipment.repository.ShipmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** Scanning in the field app picks the parcel movement the shipment rules expect. */
class WorkforceScanServiceTest {
    private val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, 0)
    private val parcel = shipment.parcels.single()
    private val worker = UUID.randomUUID()
    private var assignments = emptyList<WorkforceAssignmentSnapshot>()
    private var hubs = emptySet<UUID>()
    private val recorded = mutableListOf<RecordParcelScanRequest>()

    private val operations = mock(OperationsShipmentClient::class.java) { invocation ->
        when (invocation.method.name) {
            "workforceAssignments" -> assignments
            "caseAdminScope" -> ShipmentCaseAdminScopeSnapshot(worker, hubs)
            else -> null
        }
    }
    private val shipments = mock(ShipmentRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "findByTrackingCodeIgnoreCase" -> shipment
            "findById" -> Optional.of(shipment)
            else -> null
        }
    }
    private val parcels = mock(ParcelRepository::class.java) { invocation ->
        if (invocation.method.name == "findByCodeIgnoreCase") parcel else null
    }
    private val recorder = mock(RecordParcelScanService::class.java) { invocation ->
        recorded += invocation.getArgument<RecordParcelScanRequest>(1)
        ShipmentResponseMapper.detail(shipment)
    }
    private var routeHubs = emptySet<UUID>()
    private val network = mock(NetworkShipmentClient::class.java) { invocation ->
        if (invocation.method.name == "resolveOperationalHub") {
            val hubId = invocation.getArgument<UUID>(2)
            ShipmentOperationalHubSnapshot(hubId, "KHO", "Kho", true, hubId in routeHubs)
        } else {
            null
        }
    }
    private val views = WorkforceViewService(shipments, parcels, operations, network)
    private val service = WorkforceScanService(
        views,
        shipments,
        operations,
        recorder,
        TransactionTemplate(
            object : PlatformTransactionManager {
                override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
                override fun commit(status: TransactionStatus) = Unit
                override fun rollback(status: TransactionStatus) = Unit
            },
        ),
    )

    @Test
    fun `a courier on a started pickup job picks the parcel up at the origin hub`() {
        assignments = listOf(assignment(OperationsAssignmentType.PICKUP, OperationsAssignmentStatus.IN_PROGRESS, shipment.originHubId))

        val result = scan(courier(), WorkforceScanAction.PICK_UP).results.single()

        assertTrue(result.ok)
        val request = recorded.single()
        assertEquals(ParcelStatus.PICKED_UP, request.nextStatus)
        assertEquals(shipment.originHubId, request.hubId)
        assertEquals(assignments.single().id, request.assignmentId)
    }

    @Test
    fun `a pickup job that was not started asks the courier to start it`() {
        assignments = listOf(assignment(OperationsAssignmentType.PICKUP, OperationsAssignmentStatus.ACCEPTED, shipment.originHubId))

        val result = scan(courier(), WorkforceScanAction.PICK_UP).results.single()

        assertFalse(result.ok)
        assertEquals("assignment_not_started", result.code)
        assertEquals(assignments.single().id, result.assignmentId)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `hub staff receive a picked-up parcel only at the origin hub`() {
        parcel.status = ParcelStatus.PICKED_UP
        hubs = setOf(shipment.originHubId)

        val atOrigin = scan(hubStaff(), WorkforceScanAction.RECEIVE, shipment.originHubId).results.single()
        assertTrue(atOrigin.ok)
        assertEquals(ParcelStatus.AT_ORIGIN_HUB, recorded.single().nextStatus)

        recorded.clear()
        val elsewhere = scan(hubStaff(), WorkforceScanAction.RECEIVE, UUID.randomUUID()).results.single()
        assertFalse(elsewhere.ok)
        assertEquals("wrong_hub", elsewhere.code)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `a truck arriving at the destination hub is received on the leg it travelled`() {
        val leg = UUID.randomUUID()
        parcel.status = ParcelStatus.IN_TRANSIT
        parcel.currentRouteLegId = leg
        hubs = setOf(shipment.destinationHubId)

        scan(hubStaff(), WorkforceScanAction.RECEIVE, shipment.destinationHubId)

        val request = recorded.single()
        assertEquals(ParcelStatus.AT_DESTINATION_HUB, request.nextStatus)
        assertEquals(leg, request.routeLegId)
        assertNull(request.assignmentId)
    }

    @Test
    fun `a transit hub on the route sees and receives a truck that has not reached it yet`() {
        val transitHub = UUID.randomUUID()
        parcel.status = ParcelStatus.IN_TRANSIT
        parcel.currentRouteLegId = UUID.randomUUID()
        shipment.routeRevision = 1
        hubs = setOf(transitHub)
        routeHubs = setOf(transitHub)

        scan(hubStaff(), WorkforceScanAction.RECEIVE, transitHub)

        assertEquals(ParcelStatus.AT_TRANSIT_HUB, recorded.single().nextStatus)
    }

    @Test
    fun `a hub off the route still cannot look up the shipment by scanning it`() {
        val otherHub = UUID.randomUUID()
        parcel.status = ParcelStatus.IN_TRANSIT
        shipment.routeRevision = 1
        hubs = setOf(otherHub)

        assertThrows(ShipmentNotFoundException::class.java) {
            scan(hubStaff(), WorkforceScanAction.RECEIVE, otherHub)
        }
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `a worker with no job and no hub on the shipment cannot see it`() {
        hubs = setOf(UUID.randomUUID())

        assertThrows(ShipmentNotFoundException::class.java) {
            scan(hubStaff(), WorkforceScanAction.RECEIVE, UUID.randomUUID())
        }
    }

    private fun scan(actor: PavexActor, action: WorkforceScanAction, hubId: UUID? = null) =
        service.scan(actor, WorkforceScanRequest(shipment.trackingCode, action, hubId))

    private fun courier() = PavexActor(worker, setOf("scans.create", "assignments.read-own"))

    private fun hubStaff() = PavexActor(worker, setOf("scans.create", "shipments.read-hub"))

    private fun assignment(type: OperationsAssignmentType, status: OperationsAssignmentStatus, hubId: UUID) =
        WorkforceAssignmentSnapshot(
            id = UUID.randomUUID(),
            code = "ASG-1",
            shipmentId = shipment.id,
            sequence = 1,
            routeLegId = null,
            type = type,
            hubId = hubId,
            status = status,
            priority = 0,
            assignedAt = Instant.now(),
            acceptedAt = null,
            startedAt = null,
        )
}
