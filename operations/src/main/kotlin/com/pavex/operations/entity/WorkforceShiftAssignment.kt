package com.pavex.operations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class WorkforceShiftAssignmentStatus { SCHEDULED, CHECKED_IN, CHECKED_OUT, ABSENT, CANCELLED }

@Entity
@Table(name = "workforce_shift_assignments", schema = "operations")
class WorkforceShiftAssignment(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "shift_id", nullable = false)
    var shiftId: UUID = UUID.randomUUID(),
    @Column(name = "workforce_member_id", nullable = false)
    var workforceMemberId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: WorkforceShiftAssignmentStatus = WorkforceShiftAssignmentStatus.SCHEDULED,
    @Column(name = "assigned_by_user_id", nullable = false)
    var assignedByUserId: UUID = UUID.randomUUID(),
    @Column(name = "status_changed_by_user_id", nullable = false)
    var statusChangedByUserId: UUID = UUID.randomUUID(),
    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Instant = Instant.now(),
    @Column(name = "checked_in_at")
    var checkedInAt: Instant? = null,
    @Column(name = "checked_out_at")
    var checkedOutAt: Instant? = null,
    @Column(name = "marked_absent_at")
    var markedAbsentAt: Instant? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(length = 500)
    var note: String? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun reassign(actorUserId: UUID, now: Instant) {
        validateActor(actorUserId)
        check(status == WorkforceShiftAssignmentStatus.CANCELLED) {
            "Only a cancelled shift assignment can be restored."
        }
        status = WorkforceShiftAssignmentStatus.SCHEDULED
        assignedByUserId = actorUserId
        statusChangedByUserId = actorUserId
        assignedAt = now
        checkedInAt = null
        checkedOutAt = null
        markedAbsentAt = null
        cancelledAt = null
        note = null
        updatedAt = now
    }

    fun checkIn(userId: UUID, shiftStartsAt: Instant, shiftEndsAt: Instant, now: Instant): Boolean {
        validateActor(userId)
        if (status == WorkforceShiftAssignmentStatus.CHECKED_IN) return false
        check(status == WorkforceShiftAssignmentStatus.SCHEDULED) {
            "Only a scheduled shift assignment can be checked in."
        }
        check(!now.isBefore(shiftStartsAt.minus(Duration.ofMinutes(30)))) {
            "Check-in opens 30 minutes before the shift starts."
        }
        check(now < shiftEndsAt) { "The shift has already ended." }
        status = WorkforceShiftAssignmentStatus.CHECKED_IN
        statusChangedByUserId = userId
        checkedInAt = now
        updatedAt = now
        return true
    }

    fun checkOut(userId: UUID, now: Instant): Boolean {
        validateActor(userId)
        if (status == WorkforceShiftAssignmentStatus.CHECKED_OUT) return false
        check(status == WorkforceShiftAssignmentStatus.CHECKED_IN) {
            "Only a checked-in shift assignment can be checked out."
        }
        status = WorkforceShiftAssignmentStatus.CHECKED_OUT
        statusChangedByUserId = userId
        checkedOutAt = now
        updatedAt = now
        return true
    }

    fun markAbsent(actorUserId: UUID, shiftStartsAt: Instant, note: String?, now: Instant): Boolean {
        validateActor(actorUserId)
        validateNote(note)
        if (status == WorkforceShiftAssignmentStatus.ABSENT) return false
        check(status == WorkforceShiftAssignmentStatus.SCHEDULED) {
            "Only a scheduled shift assignment can be marked absent."
        }
        check(!now.isBefore(shiftStartsAt)) { "A member cannot be marked absent before the shift starts." }
        status = WorkforceShiftAssignmentStatus.ABSENT
        statusChangedByUserId = actorUserId
        markedAbsentAt = now
        this.note = normalizeNote(note)
        updatedAt = now
        return true
    }

    fun cancel(actorUserId: UUID, note: String?, now: Instant): Boolean {
        validateActor(actorUserId)
        validateNote(note)
        if (status == WorkforceShiftAssignmentStatus.CANCELLED) return false
        check(status !in setOf(WorkforceShiftAssignmentStatus.CHECKED_OUT, WorkforceShiftAssignmentStatus.ABSENT)) {
            "A completed shift assignment cannot be cancelled."
        }
        status = WorkforceShiftAssignmentStatus.CANCELLED
        statusChangedByUserId = actorUserId
        cancelledAt = now
        this.note = normalizeNote(note)
        updatedAt = now
        return true
    }

    companion object {
        private val ZERO_UUID: UUID = UUID(0, 0)

        fun create(shiftId: UUID, workforceMemberId: UUID, assignedByUserId: UUID, assignedAt: Instant): WorkforceShiftAssignment {
            require(shiftId != ZERO_UUID && workforceMemberId != ZERO_UUID && assignedByUserId != ZERO_UUID) {
                "Shift, workforce member and actor are required."
            }
            return WorkforceShiftAssignment(
                shiftId = shiftId,
                workforceMemberId = workforceMemberId,
                status = WorkforceShiftAssignmentStatus.SCHEDULED,
                assignedByUserId = assignedByUserId,
                statusChangedByUserId = assignedByUserId,
                assignedAt = assignedAt,
                updatedAt = assignedAt,
            )
        }

        private fun validateActor(actorUserId: UUID) {
            require(actorUserId != ZERO_UUID) { "Actor is required." }
        }

        private fun validateNote(note: String?) {
            require((note?.trim()?.length ?: 0) <= 500) { "Shift assignment note must not exceed 500 characters." }
        }

        private fun normalizeNote(note: String?): String? = note?.trim()?.takeIf { it.isNotEmpty() }
    }
}
