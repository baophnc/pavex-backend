package com.pavex.shipment.service

import com.pavex.common.security.PavexActor
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.WorkforceLookupResponse
import com.pavex.shipment.dto.WorkforceParcelResponse
import com.pavex.shipment.dto.WorkforceScanAction
import com.pavex.shipment.dto.WorkforceShipmentResponse
import com.pavex.shipment.dto.WorkforceTaskResponse
import com.pavex.shipment.dto.WorkforceTimelineEvent
import com.pavex.shipment.entity.ParcelCustodyType
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.ParcelRepository
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * What the field app shows: a worker's open jobs, and a scanned shipment the worker is
 * allowed to see (their own job, a parcel they carry, or a shipment touching their hub).
 */
@Service
class WorkforceViewService(
    private val shipments: ShipmentRepository,
    private val parcels: ParcelRepository,
    private val operations: OperationsShipmentClient,
) {
    @Transactional(readOnly = true)
    fun tasks(actor: PavexActor): List<WorkforceTaskResponse> {
        val assignments = operations.workforceAssignments(actor.userId)
        if (assignments.isEmpty()) return emptyList()
        val byId = shipments.findAllById(assignments.map { it.shipmentId }.toSet()).associateBy { it.id }
        return assignments.mapNotNull { assignment ->
            val shipment = byId[assignment.shipmentId] ?: return@mapNotNull null
            WorkforceTaskResponse(
                assignmentId = assignment.id,
                assignmentCode = assignment.code,
                type = assignment.type,
                status = assignment.status,
                hubId = assignment.hubId,
                hubName = hubName(shipment, assignment.hubId),
                routeLegId = assignment.routeLegId,
                priority = assignment.priority,
                assignedAt = assignment.assignedAt,
                acceptedAt = assignment.acceptedAt,
                startedAt = assignment.startedAt,
                shipment = view(shipment),
            )
        }
    }

    @Transactional(readOnly = true)
    fun lookup(actor: PavexActor, rawCode: String): WorkforceLookupResponse {
        val shipment = findShipment(rawCode)
        val assignments = operations.workforceAssignments(actor.userId).filter { it.shipmentId == shipment.id }
        requireVisible(actor, shipment, assignments)
        return WorkforceLookupResponse(
            shipment = view(shipment),
            suggestedActions = suggestedActions(actor, shipment, assignments),
            timeline = shipment.events
                .sortedByDescending { it.occurredAt }
                .map {
                    WorkforceTimelineEvent(
                        status = it.status,
                        code = it.code,
                        description = ShipmentVietnameseText.event(it.description),
                        locationName = it.locationName,
                        occurredAt = it.occurredAt,
                    )
                },
        )
    }

    /** A tracking code or a parcel code printed on a label. */
    fun findShipment(rawCode: String): Shipment {
        val code = ShipmentCodeNormalizer.normalizeOrNull(rawCode) ?: throw notFound()
        return if (code.contains('-')) {
            parcels.findByCodeIgnoreCase(code)?.shipment
        } else {
            shipments.findByTrackingCodeIgnoreCase(code)
        } ?: throw notFound()
    }

    fun requireVisible(actor: PavexActor, shipment: Shipment, ownAssignments: List<WorkforceAssignmentSnapshot>) {
        if (actor.seesAllHubs && actor.hasAuthority(ADMIN_READ)) return
        if (ownAssignments.isNotEmpty()) return
        if (shipment.parcels.any { it.custodyType == ParcelCustodyType.COURIER && it.custodyReferenceId == actor.userId }) return
        if (actor.hasAuthority(HUB_READ)) {
            val hubs = operations.caseAdminScope(actor.userId).hubIds
            if (touchesHubs(shipment, hubs)) return
        }
        throw notFound()
    }

    fun view(shipment: Shipment) = WorkforceShipmentResponse(
        trackingCode = shipment.trackingCode,
        merchantReference = shipment.merchantReference,
        status = shipment.status,
        serviceLevel = shipment.serviceLevel,
        sender = ShipmentResponseMapper.address(shipment.sender),
        recipient = ShipmentResponseMapper.address(shipment.recipient),
        originHubId = shipment.originHubId,
        originHubName = shipment.originHubName,
        destinationHubId = shipment.destinationHubId,
        destinationHubName = shipment.destinationHubName,
        amountToCollect = shipment.recipientCollectAmount,
        codAmount = shipment.codAmount,
        collectionStatus = shipment.collectionStatus(),
        note = shipment.note,
        parcels = shipment.parcels.sortedBy { it.sequence }.map { parcel ->
            WorkforceParcelResponse(
                parcelCode = parcel.code,
                sequence = parcel.sequence,
                status = parcel.status,
                description = parcel.description,
                isFragile = parcel.isFragile,
                chargeableWeightKg = parcel.chargeableWeightKg,
                currentHubId = parcel.currentHubId,
                currentHubName = parcel.currentHubName,
            )
        },
        updatedAt = shipment.updatedAt,
    )

    private fun suggestedActions(
        actor: PavexActor,
        shipment: Shipment,
        assignments: List<WorkforceAssignmentSnapshot>,
    ): List<WorkforceScanAction> {
        val statuses = shipment.parcels.map { it.status }.toSet()
        val types = assignments.map { it.type }.toSet()
        return buildList {
            if (OperationsAssignmentType.PICKUP in types && ParcelStatus.AWAITING_PICKUP in statuses) add(WorkforceScanAction.PICK_UP)
            if (OperationsAssignmentType.LINE_HAUL in types &&
                statuses.any { it == ParcelStatus.AT_ORIGIN_HUB || it == ParcelStatus.AT_TRANSIT_HUB }
            ) {
                add(WorkforceScanAction.DEPART)
            }
            if (OperationsAssignmentType.LAST_MILE_DELIVERY in types &&
                statuses.any { it == ParcelStatus.AT_DESTINATION_HUB || it == ParcelStatus.DELIVERY_FAILED }
            ) {
                add(WorkforceScanAction.OUT_FOR_DELIVERY)
            }
            if (actor.hasAuthority(SCAN) && actor.hasAuthority(HUB_READ)) {
                if (statuses.any { it in RECEIVABLE }) add(WorkforceScanAction.RECEIVE)
                if (statuses.any { it in ON_SHELF }) add(WorkforceScanAction.CHECK)
            }
        }
    }

    private fun hubName(shipment: Shipment, hubId: UUID): String? = when (hubId) {
        shipment.originHubId -> shipment.originHubName
        shipment.destinationHubId -> shipment.destinationHubName
        else -> shipment.parcels.firstOrNull { it.currentHubId == hubId }?.currentHubName
            ?: shipment.events.lastOrNull { it.hubId == hubId }?.locationName
    }

    private fun notFound() = ShipmentNotFoundException("shipment_not_found", "Không tìm thấy đơn hàng với mã này.")

    companion object {
        const val ADMIN_READ = "shipments.admin.read"
        const val HUB_READ = "shipments.read-hub"
        const val SCAN = "scans.create"

        val RECEIVABLE = setOf(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.RETURNING)
        val ON_SHELF = setOf(ParcelStatus.AT_ORIGIN_HUB, ParcelStatus.AT_TRANSIT_HUB, ParcelStatus.AT_DESTINATION_HUB)

        fun touchesHubs(shipment: Shipment, hubIds: Set<UUID>): Boolean =
            hubIds.isNotEmpty() && (
                shipment.originHubId in hubIds ||
                    shipment.destinationHubId in hubIds ||
                    shipment.parcels.any { it.currentHubId in hubIds } ||
                    shipment.events.any { it.hubId in hubIds }
                )
    }
}
