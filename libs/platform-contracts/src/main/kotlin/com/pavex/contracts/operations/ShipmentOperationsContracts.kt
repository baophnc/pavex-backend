package com.pavex.contracts.operations

import java.util.UUID

enum class OperationsCapability {
    PARCEL_SCANNING,
    PICKUP,
    LAST_MILE_DELIVERY,
    LINE_HAUL,

    /** Receiving and reviewing courier cash at a hub (billing). */
    CASH_HANDLING,
}

enum class OperationsAssignmentType {
    PICKUP,
    ORIGIN_HUB_HANDLING,
    LINE_HAUL,
    TRANSIT_HUB_HANDLING,
    DESTINATION_HUB_HANDLING,
    LAST_MILE_DELIVERY,
}

enum class OperationsAssignmentStatus {
    BLOCKED,
    READY,
    PENDING,
    ASSIGNED,
    ACCEPTED,
    REJECTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

data class ShipmentOperationsRouteLeg(
    val routeLegId: UUID,
    val sequence: Int,
    val fromHubId: UUID,
    val toHubId: UUID,
)

data class PlanShipmentOperationsRequest(
    val originHubId: UUID,
    val destinationHubId: UUID,
    val routeLegs: List<ShipmentOperationsRouteLeg>,
    val priority: Int = 50,
    /** The parcels were handed in at the origin hub's counter, so no courier pickup is planned. */
    val startsAtOriginHub: Boolean = false,
)

data class ShipmentOperationsAssignmentSnapshot(
    val id: UUID,
    val shipmentId: UUID,
    val sequence: Int,
    val routeLegId: UUID?,
    val type: OperationsAssignmentType,
    val hubId: UUID,
    val assigneeWorkforceMemberId: UUID?,
    val status: OperationsAssignmentStatus,
)

data class ShipmentOperationsPlanResponse(
    val shipmentId: UUID,
    val assignments: List<ShipmentOperationsAssignmentSnapshot>,
)

data class CancelShipmentOperationsRequest(
    val reason: String,
)

data class OperationsAccessCheckRequest(
    val userId: UUID,
    val hubId: UUID,
    val capability: OperationsCapability,
)

data class OperationsAccessCheckResponse(
    val allowed: Boolean,
)

data class ShipmentAssignmentAccessRequest(
    val actorUserId: UUID,
    val expectedType: OperationsAssignmentType,
    val hubId: UUID,
    val routeLegId: UUID? = null,
)

data class ShipmentAssignmentCompletionRequest(
    val actorUserId: UUID? = null,
    val expectedType: OperationsAssignmentType,
    val hubId: UUID,
    val routeLegId: UUID? = null,
    val note: String? = null,
)

data class ShipmentCaseAdminScopeSnapshot(
    val userId: UUID,
    val hubIds: Set<UUID> = emptySet(),
)

/** An open assignment of one worker, as the field app lists it. */
data class WorkforceAssignmentSnapshot(
    val id: UUID,
    val code: String,
    val shipmentId: UUID,
    val sequence: Int,
    val routeLegId: UUID?,
    val type: OperationsAssignmentType,
    val hubId: UUID,
    val status: OperationsAssignmentStatus,
    val priority: Int,
    val assignedAt: java.time.Instant?,
    val acceptedAt: java.time.Instant?,
    val startedAt: java.time.Instant?,
)

/** A user's work as Notifications sees it: their workforce kind (null if not staff) and active hubs. */
data class WorkPersonSnapshot(
    val userId: UUID,
    val kind: String?,
    val hubIds: List<UUID>,
)

/** One active person of a hub, to address a hub message to. */
data class HubPersonSnapshot(
    val userId: UUID,
    val kind: String,
)
