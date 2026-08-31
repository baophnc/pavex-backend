package com.pavex.operations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "hub_memberships", schema = "operations")
class HubMembership(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "workforce_member_id", nullable = false)
    var workforceMemberId: UUID = UUID.randomUUID(),
    @Column(name = "hub_id", nullable = false)
    var hubId: UUID = UUID.randomUUID(),
    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Instant = Instant.now(),
    @Column(name = "unassigned_at")
    var unassignedAt: Instant? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun reactivate(primary: Boolean, now: Instant) {
        isActive = true
        isPrimary = primary
        assignedAt = now
        unassignedAt = null
        updatedAt = now
    }

    fun setPrimary(primary: Boolean, now: Instant) {
        if (isPrimary == primary) return
        isPrimary = primary
        updatedAt = now
    }

    fun deactivate(now: Instant) {
        if (!isActive) return
        isActive = false
        isPrimary = false
        unassignedAt = now
        updatedAt = now
    }
}
