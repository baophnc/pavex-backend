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

enum class WorkforceAvailabilityStatus { OFF_DUTY, AVAILABLE, UNAVAILABLE }

@Entity
@Table(name = "workforce_availabilities", schema = "operations")
class WorkforceAvailability(
    @Id
    @Column(name = "workforce_member_id")
    var workforceMemberId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: WorkforceAvailabilityStatus = WorkforceAvailabilityStatus.OFF_DUTY,
    @Column(name = "max_concurrent_assignments", nullable = false)
    var maxConcurrentAssignments: Int = 1,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun setStatus(next: WorkforceAvailabilityStatus, now: Instant): Boolean {
        if (status == next) return false
        status = next
        updatedAt = now
        return true
    }

    fun setMaximum(value: Int, now: Instant): Boolean {
        require(value in 1..100) { "Maximum concurrent assignments must be between 1 and 100." }
        if (maxConcurrentAssignments == value) return false
        maxConcurrentAssignments = value
        updatedAt = now
        return true
    }
}
