package com.pavex.operations.dto

import com.pavex.operations.entity.WorkShiftStatus
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceShiftAssignmentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateWorkShiftRequest(
    val hubId: UUID,
    @field:NotBlank @field:Size(max = 100) val name: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class UpdateWorkShiftRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class CancelWorkShiftRequest(@field:NotBlank @field:Size(max = 500) val reason: String)

data class AssignWorkforceShiftRequest(val workforceMemberId: UUID)

data class MarkShiftAbsentRequest(@field:Size(max = 500) val note: String? = null)

data class WorkShiftResponse(
    val id: UUID,
    val hubId: UUID,
    val hubCode: String?,
    val hubName: String?,
    val code: String,
    val name: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: WorkShiftStatus,
    val rosterCount: Int,
    val createdByUserId: UUID,
    val cancelledByUserId: UUID?,
    val cancelReason: String?,
    val cancelledAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class WorkShiftDetailResponse(
    val shift: WorkShiftResponse,
    val roster: List<WorkforceShiftAssignmentResponse>,
)

data class WorkforceShiftAssignmentResponse(
    val id: UUID,
    val shiftId: UUID,
    val workforceMemberId: UUID,
    val employeeCode: String,
    val kind: WorkforceMemberKind,
    val status: WorkforceShiftAssignmentStatus,
    val assignedByUserId: UUID,
    val statusChangedByUserId: UUID,
    val assignedAt: Instant,
    val checkedInAt: Instant?,
    val checkedOutAt: Instant?,
    val markedAbsentAt: Instant?,
    val cancelledAt: Instant?,
    val note: String?,
    val updatedAt: Instant,
)

data class MyWorkShiftResponse(
    val shiftId: UUID,
    val code: String,
    val hubId: UUID,
    val hubCode: String?,
    val hubName: String?,
    val name: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val shiftStatus: WorkShiftStatus,
    val assignmentStatus: WorkforceShiftAssignmentStatus,
    val checkedInAt: Instant?,
    val checkedOutAt: Instant?,
    val markedAbsentAt: Instant?,
    val cancelledAt: Instant?,
    val note: String?,
)
