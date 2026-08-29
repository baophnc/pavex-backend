package com.pavex.pricing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class PricingAuditEntityType { RATE_PLAN, RATE_RULE }
enum class PricingAuditAction { CREATED, UPDATED, RULE_UPSERTED, RULE_DELETED, REVISION_CREATED, ACTIVATED, RETIRED, DELETED }

@Entity
@Table(name = "audit_logs", schema = "pricing")
class PricingAuditLog(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    var entityType: PricingAuditEntityType = PricingAuditEntityType.RATE_PLAN,
    @Column(name = "entity_id", nullable = false)
    var entityId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var action: PricingAuditAction = PricingAuditAction.CREATED,
    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: UUID = UUID.randomUUID(),
    @Column(name = "before_json", columnDefinition = "text")
    var beforeJson: String? = null,
    @Column(name = "after_json", columnDefinition = "text")
    var afterJson: String? = null,
    @Column(length = 500)
    var reason: String? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
)
