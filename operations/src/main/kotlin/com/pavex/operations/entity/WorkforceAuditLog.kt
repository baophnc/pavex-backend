package com.pavex.operations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class WorkforceAuditAction { MEMBER_CREATED, STATUS_CHANGED, HUB_ASSIGNED, HUB_UNASSIGNED, CAPACITY_CHANGED }

@Entity
@Table(name = "workforce_audit_logs", schema = "operations")
class WorkforceAuditLog(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "workforce_member_id", nullable = false)
    var workforceMemberId: UUID = UUID.randomUUID(),
    @Column(name = "hub_id")
    var hubId: UUID? = null,
    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var action: WorkforceAuditAction = WorkforceAuditAction.MEMBER_CREATED,
    @Column(name = "previous_value", length = 100)
    var previousValue: String? = null,
    @Column(name = "new_value", length = 100)
    var newValue: String? = null,
    @Column(length = 500)
    var reason: String? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
)
