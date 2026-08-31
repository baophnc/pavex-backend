package com.pavex.operations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox_messages", schema = "operations")
class OperationsOutboxMessage(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "event_type", nullable = false, length = 200)
    var eventType: String = "",
    @Column(name = "aggregate_type", nullable = false, length = 100)
    var aggregateType: String = "",
    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: UUID = UUID.randomUUID(),
    @Column(nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}",
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
    @Column(name = "available_at", nullable = false)
    var availableAt: Instant = Instant.now(),
    @Column(name = "processed_at")
    var processedAt: Instant? = null,
    @Column(name = "dead_lettered_at")
    var deadLetteredAt: Instant? = null,
    @Column(name = "discarded_at")
    var discardedAt: Instant? = null,
    @Column(name = "discarded_by_user_id")
    var discardedByUserId: UUID? = null,
    @Column(name = "discard_reason", length = 500)
    var discardReason: String? = null,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,
    @Column(name = "last_error", length = 2000)
    var lastError: String? = null,
    @Column(name = "lock_id")
    var lockId: UUID? = null,
    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,
)
