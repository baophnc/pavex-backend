package com.pavex.partners.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_role_outbox", schema = "partners")
class MerchantRoleOutboxMessage(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var enabled: Boolean = false,
    @Column(name = "assigned_by_user_id")
    var assignedByUserId: UUID? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
    @Column(name = "available_at", nullable = false)
    var availableAt: Instant = Instant.now(),
    @Column(name = "processed_at")
    var processedAt: Instant? = null,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,
    @Column(name = "last_error", length = 2000)
    var lastError: String? = null,
)
