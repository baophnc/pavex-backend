package com.pavex.shipment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox_events", schema = "shipments")
class ShipmentOutboxEvent(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: UUID = UUID.randomUUID(),
    @Column(name = "aggregate_code", nullable = false, length = 40)
    var aggregateCode: String = "",
    @Column(name = "event_type", nullable = false, length = 120)
    var eventType: String = "",
    @Column(name = "event_version", nullable = false)
    var eventVersion: Int = 1,
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    var payloadJson: String = "{}",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ShipmentOutboxStatus = ShipmentOutboxStatus.PENDING,
    @Column(nullable = false)
    var attempts: Int = 0,
    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
    @Column(name = "last_error", length = 2000)
    var lastError: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
