package com.pavex.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** An integration event already applied; RabbitMQ delivers at least once. */
@Entity
@Table(name = "processed_events", schema = "billing")
class ProcessedEvent(
    @Id
    @Column(name = "event_id")
    var eventId: UUID = UUID.randomUUID(),
    @Column(name = "event_type", nullable = false, length = 120)
    var eventType: String = "",
    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.EPOCH,
)
