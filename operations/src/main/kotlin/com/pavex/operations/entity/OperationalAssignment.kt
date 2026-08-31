package com.pavex.operations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class OperationalAssignmentType {
    PICKUP,
    ORIGIN_HUB_HANDLING,
    LINE_HAUL,
    DESTINATION_HUB_HANDLING,
    LAST_MILE_DELIVERY,
}

enum class OperationalAssignmentStatus {
    PENDING,
    ASSIGNED,
    ACCEPTED,
    REJECTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

@Entity
@Table(name = "operational_assignments", schema = "operations")
class OperationalAssignment(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 24)
    var code: String = "",
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var sequence: Int = 1,
    @Column(name = "route_leg_id")
    var routeLegId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var type: OperationalAssignmentType = OperationalAssignmentType.PICKUP,
    @Enumerated(EnumType.STRING)
    @Column(name = "required_kind", nullable = false, length = 32)
    var requiredKind: WorkforceMemberKind = WorkforceMemberKind.COURIER,
    @Column(name = "hub_id", nullable = false)
    var hubId: UUID = UUID.randomUUID(),
    @Column(name = "assignee_workforce_member_id")
    var assigneeWorkforceMemberId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OperationalAssignmentStatus = OperationalAssignmentStatus.PENDING,
    @Column(nullable = false)
    var priority: Int = 50,
    @Column(name = "assigned_by_user_id")
    var assignedByUserId: UUID? = null,
    @Column(name = "assigned_at")
    var assignedAt: Instant? = null,
    @Column(name = "accepted_at")
    var acceptedAt: Instant? = null,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "resolution_note", length = 500)
    var resolutionNote: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun assign(memberId: UUID, actorUserId: UUID?, now: Instant): Boolean {
        require(memberId != ZERO_UUID) { "Assignee is required." }
        check(status in ASSIGNABLE_STATUSES) { "This assignment cannot be assigned or reassigned." }
        if (status == OperationalAssignmentStatus.ASSIGNED && assigneeWorkforceMemberId == memberId) return false
        assigneeWorkforceMemberId = memberId
        assignedByUserId = actorUserId
        assignedAt = now
        acceptedAt = null
        startedAt = null
        completedAt = null
        cancelledAt = null
        resolutionNote = null
        status = OperationalAssignmentStatus.ASSIGNED
        updatedAt = now
        return true
    }

    fun requeue(now: Instant): Boolean {
        check(status == OperationalAssignmentStatus.ASSIGNED || status == OperationalAssignmentStatus.ACCEPTED) {
            "Only assigned or accepted assignments can be requeued."
        }
        assigneeWorkforceMemberId = null
        assignedByUserId = null
        assignedAt = null
        acceptedAt = null
        resolutionNote = null
        status = OperationalAssignmentStatus.PENDING
        updatedAt = now
        return true
    }

    fun accept(memberId: UUID, now: Instant): Boolean {
        ensureAssignee(memberId)
        if (status == OperationalAssignmentStatus.ACCEPTED) return false
        ensureStatus(OperationalAssignmentStatus.ASSIGNED)
        status = OperationalAssignmentStatus.ACCEPTED
        acceptedAt = now
        updatedAt = now
        return true
    }

    fun reject(memberId: UUID, reason: String, now: Instant): Boolean {
        ensureAssignee(memberId)
        ensureStatus(OperationalAssignmentStatus.ASSIGNED)
        val normalized = requiredNote(reason, "A rejection reason is required.")
        resolutionNote = normalized
        assigneeWorkforceMemberId = null
        status = OperationalAssignmentStatus.REJECTED
        updatedAt = now
        return true
    }

    fun start(memberId: UUID, now: Instant): Boolean {
        ensureAssignee(memberId)
        if (status == OperationalAssignmentStatus.IN_PROGRESS) return false
        ensureStatus(OperationalAssignmentStatus.ACCEPTED)
        status = OperationalAssignmentStatus.IN_PROGRESS
        startedAt = now
        updatedAt = now
        return true
    }

    fun complete(memberId: UUID, note: String?, now: Instant): Boolean {
        ensureAssignee(memberId)
        if (status == OperationalAssignmentStatus.COMPLETED) return false
        ensureStatus(OperationalAssignmentStatus.IN_PROGRESS)
        resolutionNote = optionalNote(note)
        status = OperationalAssignmentStatus.COMPLETED
        completedAt = now
        updatedAt = now
        return true
    }

    fun cancel(reason: String, now: Instant): Boolean {
        if (status == OperationalAssignmentStatus.CANCELLED) return false
        check(status != OperationalAssignmentStatus.COMPLETED) { "A completed assignment cannot be cancelled." }
        resolutionNote = requiredNote(reason, "A cancellation reason is required.")
        status = OperationalAssignmentStatus.CANCELLED
        cancelledAt = now
        updatedAt = now
        return true
    }

    private fun ensureAssignee(memberId: UUID) {
        check(assigneeWorkforceMemberId == memberId) { "This assignment belongs to another workforce member." }
    }

    private fun ensureStatus(expected: OperationalAssignmentStatus) {
        check(status == expected) { "Assignment must be $expected before this operation." }
    }

    companion object {
        private val ZERO_UUID = UUID(0, 0)
        private val ASSIGNABLE_STATUSES = setOf(
            OperationalAssignmentStatus.PENDING,
            OperationalAssignmentStatus.ASSIGNED,
            OperationalAssignmentStatus.REJECTED,
        )

        fun create(
            shipmentId: UUID,
            sequence: Int,
            routeLegId: UUID?,
            type: OperationalAssignmentType,
            requiredKind: WorkforceMemberKind,
            hubId: UUID,
            priority: Int,
            now: Instant,
        ): OperationalAssignment {
            require(shipmentId != ZERO_UUID && hubId != ZERO_UUID && sequence > 0) {
                "Shipment, hub and sequence are required."
            }
            require(priority in 0..100) { "Assignment priority must be between 0 and 100." }
            val id = UUID.randomUUID()
            val compact = id.toString().replace("-", "").uppercase()
            return OperationalAssignment(
                id = id,
                code = "PAVEX-ASG-${compact.take(11)}",
                shipmentId = shipmentId,
                sequence = sequence,
                routeLegId = routeLegId,
                type = type,
                requiredKind = requiredKind,
                hubId = hubId,
                status = OperationalAssignmentStatus.PENDING,
                priority = priority,
                createdAt = now,
                updatedAt = now,
            )
        }

        private fun requiredNote(value: String, emptyMessage: String): String =
            optionalNote(value) ?: throw IllegalArgumentException(emptyMessage)

        private fun optionalNote(value: String?): String? {
            val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            require(normalized.length <= 500) { "Assignment note must not exceed 500 characters." }
            return normalized
        }
    }
}
