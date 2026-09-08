package com.pavex.operations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class OperationalAssignmentEventType {
    PLANNED,
    ACTIVATED,
    ASSIGNED,
    REASSIGNED,
    ACCEPTED,
    REJECTED,
    STARTED,
    COMPLETED,
    CANCELLED,
    REQUEUED,
}

@Entity
@Table(name = "operational_assignment_event_logs", schema = "operations")
class OperationalAssignmentEventLog(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "assignment_id", nullable = false)
    var assignmentId: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    var eventType: OperationalAssignmentEventType = OperationalAssignmentEventType.PLANNED,
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    var previousStatus: OperationalAssignmentStatus? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    var newStatus: OperationalAssignmentStatus = OperationalAssignmentStatus.PENDING,
    @Column(name = "workforce_member_id")
    var workforceMemberId: UUID? = null,
    @Column(name = "actor_user_id")
    var actorUserId: UUID? = null,
    @Column(length = 500)
    var reason: String? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
)
