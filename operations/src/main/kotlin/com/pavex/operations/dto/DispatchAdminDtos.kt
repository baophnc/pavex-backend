package com.pavex.operations.dto

import com.pavex.operations.entity.OperationalAssignmentEventType
import com.pavex.operations.entity.OperationalAssignmentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class OperationalAssignmentEventResponse(
    val eventId: UUID,
    val assignmentId: UUID,
    val shipmentId: UUID,
    val eventType: OperationalAssignmentEventType,
    val previousStatus: OperationalAssignmentStatus?,
    val newStatus: OperationalAssignmentStatus,
    val workforceMemberId: UUID?,
    val actorUserId: UUID?,
    val reason: String?,
    val occurredAt: Instant,
)

data class OperationalAssignmentEventPage(
    val items: List<OperationalAssignmentEventResponse>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val totalPages: Int,
)

data class OperationsOutboxSummaryResponse(
    val pendingCount: Long,
    val deadLetteredCount: Long,
    val discardedCount: Long,
    val oldestPendingOccurredAt: Instant?,
    val oldestPendingAgeSeconds: Long?,
)

data class OperationsOutboxDeadLetterResponse(
    val messageId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val payload: String,
    val occurredAt: Instant,
    val deadLetteredAt: Instant,
    val attemptCount: Int,
    val lastError: String?,
)

data class OperationsOutboxDeadLetterPage(
    val items: List<OperationsOutboxDeadLetterResponse>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val totalPages: Int,
)

data class DiscardOperationsOutboxRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)
