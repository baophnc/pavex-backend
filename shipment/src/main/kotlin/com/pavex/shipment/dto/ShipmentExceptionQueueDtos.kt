package com.pavex.shipment.dto

import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentStatus
import java.time.Instant
import java.util.UUID

enum class ShipmentExceptionQueueKind {
    RETURN,
    FORWARD,
    CANCELLATION,
    WEIGHT_REVIEW,
}

enum class ShipmentExceptionQueueStatus {
    PENDING_APPROVAL,
    APPROVED,
    PROCESSING,
    PENDING_REVIEW,
    REJECTED,
    COMPLETED,
}

data class ShipmentExceptionQueueItemResponse(
    val id: UUID,
    val shipmentId: UUID,
    val trackingCode: String,
    val kind: ShipmentExceptionQueueKind,
    val status: ShipmentExceptionQueueStatus,
    val reason: String,
    val requestedAt: Instant,
    val requestedByUserId: UUID,
    val reviewedByUserId: UUID?,
    val reviewedAt: Instant?,
    val parcelId: UUID?,
    val parcelCode: String?,
    val hubId: UUID?,
    val shipmentStatus: ShipmentStatus,
    val lifecycleStatus: ShipmentLifecycleStatus,
    val originHubCode: String,
    val originHubName: String,
    val destinationHubCode: String,
    val destinationHubName: String,
    val isOnHold: Boolean,
    val holdReason: String?,
)

data class ShipmentExceptionQueueSummaryResponse(
    val pendingApproval: Long,
    val approvedOrProcessing: Long,
    val pendingWeightReview: Long,
    val cancellationPending: Long,
    val returnPending: Long,
    val forwardPending: Long,
)
