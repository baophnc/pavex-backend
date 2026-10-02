package com.pavex.shipment.service

import com.pavex.contracts.network.ShipmentOperationalHubSnapshot
import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.OperationsCapability
import com.pavex.contracts.operations.ShipmentOperationsAssignmentSnapshot
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.RecordParcelScanRequest
import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentApiException
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentForbiddenException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ShipmentOperationsScanGuard(
    private val operations: OperationsShipmentClient,
) {
    fun validate(
        actorUserId: UUID,
        target: ParcelScanTarget,
        hub: ShipmentOperationalHubSnapshot,
        request: RecordParcelScanRequest,
    ): ShipmentScanOperationsContext {
        val nextStatus = request.nextStatus
        if (nextStatus == ParcelStatus.DELIVERED || nextStatus == ParcelStatus.DELIVERY_FAILED) {
            throw InvalidShipmentRequestException(
                "delivery_scan_not_supported",
                "Delivery success or failure must be recorded through the delivery-attempt API with proof and an operational assignment.",
            )
        }

        val checkpoint = nextStatus == null || nextStatus == target.parcelStatus
        if (checkpoint && target.currentHubId != hub.hubId) {
            throw InvalidShipmentRequestException(
                "scan_hub_mismatch",
                "A parcel checkpoint must be recorded at the parcel's current hub.",
            )
        }

        val warehouseRequired = nextStatus == null || nextStatus in WAREHOUSE_STATUSES
        if (warehouseRequired && !operations.canOperateAtHub(actorUserId, hub.hubId, OperationsCapability.PARCEL_SCANNING)) {
            throw ShipmentForbiddenException(
                "scan_hub_forbidden",
                "The current user is not assigned to scan parcels at this hub.",
            )
        }

        val ownedAssignment = when (nextStatus) {
            ParcelStatus.PICKED_UP -> authorizeOwned(
                actorUserId,
                target,
                request,
                OperationsAssignmentType.PICKUP,
                target.originHubId,
                routeLegRequired = false,
            )
            ParcelStatus.IN_TRANSIT -> authorizeOwned(
                actorUserId,
                target,
                request,
                OperationsAssignmentType.LINE_HAUL,
                hub.hubId,
                routeLegRequired = true,
            )
            ParcelStatus.OUT_FOR_DELIVERY -> authorizeOwned(
                actorUserId,
                target,
                request,
                OperationsAssignmentType.LAST_MILE_DELIVERY,
                target.destinationHubId,
                routeLegRequired = false,
            )
            else -> null
        }

        val arrivingLineHaul = if (nextStatus in LINE_HAUL_ARRIVAL_STATUSES) {
            val routeLegId = request.routeLegId ?: throw InvalidShipmentRequestException(
                "route_leg_required",
                "Destination-hub receipt requires the reserved route-leg identifier.",
            )
            operations.findRouteLegAssignment(target.shipmentId, routeLegId)
                ?.also(::requireCompletable)
                ?: throw ShipmentConflictException(
                    "linehaul_assignment_missing",
                    "The reserved route leg does not have a matching line-haul assignment.",
                )
        } else null

        val handlingType = when {
            nextStatus == ParcelStatus.AT_ORIGIN_HUB -> OperationsAssignmentType.ORIGIN_HUB_HANDLING
            // Counter shipments start on the origin shelf and are never "received"; a stock check finishes their handling.
            checkpoint && target.parcelStatus == ParcelStatus.AT_ORIGIN_HUB -> OperationsAssignmentType.ORIGIN_HUB_HANDLING
            checkpoint && target.parcelStatus == ParcelStatus.AT_TRANSIT_HUB -> OperationsAssignmentType.TRANSIT_HUB_HANDLING
            checkpoint && target.parcelStatus == ParcelStatus.AT_DESTINATION_HUB -> OperationsAssignmentType.DESTINATION_HUB_HANDLING
            else -> null
        }
        val handlingAssignment = handlingType?.let { type ->
            val assignment = operations.findTypeAssignment(target.shipmentId, type, hub.hubId)
                ?: throw ShipmentConflictException(
                    "hub_handling_assignment_missing",
                    "This hub has no handling step for the shipment.",
                )
            // Whoever at the hub scans the parcel in takes the handling over; no separate "accept" first.
            if (assignment.status in COMPLETABLE_STATUSES) {
                assignment
            } else {
                operations.claimHubHandling(
                    shipmentId = target.shipmentId,
                    assignmentId = assignment.id,
                    actorUserId = actorUserId,
                    expectedType = type,
                    hubId = hub.hubId,
                    routeLegId = assignment.routeLegId,
                ).also(::requireCompletable)
            }
        }

        return ShipmentScanOperationsContext(ownedAssignment, arrivingLineHaul, handlingAssignment)
    }

    fun afterPersist(
        actorUserId: UUID,
        target: ParcelScanTarget,
        request: RecordParcelScanRequest,
        context: ShipmentScanOperationsContext,
        response: ShipmentDetailResponse,
    ) {
        if (
            request.nextStatus == ParcelStatus.PICKED_UP &&
            context.ownedAssignment != null &&
            response.parcels.all { it.status != ParcelStatus.AWAITING_PICKUP }
        ) {
            complete(
                target,
                context.ownedAssignment,
                actorUserId,
                OperationsAssignmentType.PICKUP,
                target.originHubId,
                null,
                "All active parcels were collected for pickup.",
            )
        }

        context.handlingAssignment
            ?.takeIf { allParcelsHandled(response, it) }
            ?.let { assignment ->
            complete(
                target,
                assignment,
                actorUserId,
                assignment.type,
                assignment.hubId,
                assignment.routeLegId,
                "Shipment movement confirmed completion of hub handling.",
            )
        }

        context.arrivingLineHaul?.let { assignment ->
            if (allParcelsArrived(response, request, assignment)) {
                complete(
                    target,
                    assignment,
                    actorUserId,
                    OperationsAssignmentType.LINE_HAUL,
                    assignment.hubId,
                    assignment.routeLegId,
                    "Reserved route leg was received at the destination hub.",
                )
                handleOnArrival(actorUserId, target, request, response)
            }
        }
    }

    /**
     * Receiving the truck is the hub's handling step, as receiving from the courier is at the origin:
     * the person who scans it in takes the handling and it completes once every parcel is in, which
     * releases the next leg or the last-mile delivery. Best effort, because the scan itself is already
     * saved; a later "Kiểm kho" scan takes over and completes the handling the same way.
     */
    private fun handleOnArrival(
        actorUserId: UUID,
        target: ParcelScanTarget,
        request: RecordParcelScanRequest,
        response: ShipmentDetailResponse,
    ) {
        val type = when (request.nextStatus) {
            ParcelStatus.AT_TRANSIT_HUB -> OperationsAssignmentType.TRANSIT_HUB_HANDLING
            ParcelStatus.AT_DESTINATION_HUB -> OperationsAssignmentType.DESTINATION_HUB_HANDLING
            else -> return
        }
        try {
            val handling = operations.findTypeAssignment(target.shipmentId, type, request.hubId) ?: return
            val claimed = if (handling.status in COMPLETABLE_STATUSES) {
                handling
            } else {
                operations.claimHubHandling(target.shipmentId, handling.id, actorUserId, type, request.hubId, handling.routeLegId)
            }
            if (claimed.status != OperationsAssignmentStatus.COMPLETED && allParcelsHandled(response, claimed)) {
                complete(
                    target,
                    claimed,
                    actorUserId,
                    type,
                    claimed.hubId,
                    claimed.routeLegId,
                    "Parcels were received at the hub.",
                )
            }
        } catch (exception: ShipmentApiException) {
            log.warn(
                "Hub handling of shipment {} was not taken over on arrival ({}); a stock check scan can finish it.",
                target.shipmentId,
                exception.code,
            )
        }
    }

    private fun authorizeOwned(
        actorUserId: UUID,
        target: ParcelScanTarget,
        request: RecordParcelScanRequest,
        expectedType: OperationsAssignmentType,
        hubId: UUID,
        routeLegRequired: Boolean,
    ): ShipmentOperationsAssignmentSnapshot {
        val assignmentId = request.assignmentId ?: throw InvalidShipmentRequestException(
            "assignment_required",
            "This parcel movement requires an operational assignment.",
        )
        val routeLegId = request.routeLegId
        if (routeLegRequired && routeLegId == null) {
            throw InvalidShipmentRequestException(
                "route_leg_required",
                "Line-haul movement requires the reserved route-leg identifier.",
            )
        }
        return operations.authorizeAssignment(
            shipmentId = target.shipmentId,
            assignmentId = assignmentId,
            actorUserId = actorUserId,
            expectedType = expectedType,
            hubId = hubId,
            routeLegId = routeLegId,
        )
    }

    private fun requireCompletable(assignment: ShipmentOperationsAssignmentSnapshot) {
        if (assignment.status !in COMPLETABLE_STATUSES) {
            throw ShipmentConflictException(
                "assignment_not_ready",
                "The matching operational assignment must be accepted or in progress before this parcel movement.",
            )
        }
    }

    private fun allParcelsHandled(
        response: ShipmentDetailResponse,
        assignment: ShipmentOperationsAssignmentSnapshot,
    ): Boolean {
        val expectedStatus = when (assignment.type) {
            OperationsAssignmentType.ORIGIN_HUB_HANDLING -> ParcelStatus.AT_ORIGIN_HUB
            OperationsAssignmentType.TRANSIT_HUB_HANDLING -> ParcelStatus.AT_TRANSIT_HUB
            OperationsAssignmentType.DESTINATION_HUB_HANDLING -> ParcelStatus.AT_DESTINATION_HUB
            else -> return false
        }
        return response.parcels.all {
            it.status == expectedStatus && it.currentHubId == assignment.hubId
        }
    }

    private fun allParcelsArrived(
        response: ShipmentDetailResponse,
        request: RecordParcelScanRequest,
        assignment: ShipmentOperationsAssignmentSnapshot,
    ): Boolean = response.parcels.all {
        it.status == request.nextStatus &&
            it.currentHubId == request.hubId &&
            it.currentRouteLegId == assignment.routeLegId
    }

    private fun complete(
        target: ParcelScanTarget,
        assignment: ShipmentOperationsAssignmentSnapshot,
        actorUserId: UUID,
        type: OperationsAssignmentType,
        hubId: UUID,
        routeLegId: UUID?,
        note: String,
    ) {
        operations.completeAssignment(
            shipmentId = target.shipmentId,
            assignmentId = assignment.id,
            actorUserId = actorUserId,
            expectedType = type,
            hubId = hubId,
            routeLegId = routeLegId,
            note = note,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShipmentOperationsScanGuard::class.java)
        private val WAREHOUSE_STATUSES = setOf(
            ParcelStatus.AT_ORIGIN_HUB,
            ParcelStatus.AT_TRANSIT_HUB,
            ParcelStatus.AT_DESTINATION_HUB,
            ParcelStatus.RETURNING,
            ParcelStatus.RETURNED,
        )
        private val LINE_HAUL_ARRIVAL_STATUSES = setOf(
            ParcelStatus.AT_TRANSIT_HUB,
            ParcelStatus.AT_DESTINATION_HUB,
        )
        private val COMPLETABLE_STATUSES = setOf(
            OperationsAssignmentStatus.ACCEPTED,
            OperationsAssignmentStatus.IN_PROGRESS,
            OperationsAssignmentStatus.COMPLETED,
        )
    }
}

data class ShipmentScanOperationsContext(
    val ownedAssignment: ShipmentOperationsAssignmentSnapshot?,
    val arrivingLineHaul: ShipmentOperationsAssignmentSnapshot?,
    val handlingAssignment: ShipmentOperationsAssignmentSnapshot?,
)
