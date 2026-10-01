package com.pavex.operations.dto

import com.pavex.operations.entity.OperationalAssignment
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.entity.OperationalAssignmentType
import com.pavex.operations.entity.WorkforceAvailabilityStatus
import com.pavex.operations.entity.WorkforceMemberKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class AssignOperationalWorkRequest(val workforceMemberId: UUID)
data class CancelOperationalAssignmentRequest(@field:NotBlank @field:Size(max = 500) val reason: String)
data class RejectOwnAssignmentRequest(@field:NotBlank @field:Size(max = 500) val reason: String)
data class CompleteOwnAssignmentRequest(@field:Size(max = 500) val note: String? = null)
data class SetMyAvailabilityRequest(val status: WorkforceAvailabilityStatus)

data class ShipmentAssignmentRouteLeg(
    val routeLegId: UUID,
    val sequence: Int,
    val fromHubId: UUID,
    val toHubId: UUID,
)

data class ShipmentAssignmentPlan(
    val shipmentId: UUID,
    val originHubId: UUID,
    val destinationHubId: UUID,
    val routeLegs: List<ShipmentAssignmentRouteLeg>,
    val priority: Int = 50,
    val startsAtOriginHub: Boolean = false,
)

data class OperationalAssignmentResponse(
    val id: UUID,
    val code: String,
    val shipmentId: UUID,
    val sequence: Int,
    val routeLegId: UUID?,
    val type: OperationalAssignmentType,
    val requiredKind: WorkforceMemberKind,
    val hubId: UUID,
    val assigneeWorkforceMemberId: UUID?,
    val status: OperationalAssignmentStatus,
    val priority: Int,
    val assignedByUserId: UUID?,
    val assignedAt: Instant?,
    val acceptedAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val resolutionNote: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(value: OperationalAssignment) = OperationalAssignmentResponse(
            id = value.id,
            code = value.code,
            shipmentId = value.shipmentId,
            sequence = value.sequence,
            routeLegId = value.routeLegId,
            type = value.type,
            requiredKind = value.requiredKind,
            hubId = value.hubId,
            assigneeWorkforceMemberId = value.assigneeWorkforceMemberId,
            status = value.status,
            priority = value.priority,
            assignedByUserId = value.assignedByUserId,
            assignedAt = value.assignedAt,
            acceptedAt = value.acceptedAt,
            startedAt = value.startedAt,
            completedAt = value.completedAt,
            cancelledAt = value.cancelledAt,
            resolutionNote = value.resolutionNote,
            createdAt = value.createdAt,
            updatedAt = value.updatedAt,
        )
    }
}

data class MyAvailabilityResponse(
    val workforceMemberId: UUID,
    val status: WorkforceAvailabilityStatus,
    val maxConcurrentAssignments: Int,
    val activeAssignments: Int,
    val remainingCapacity: Int,
    val updatedAt: Instant,
)
