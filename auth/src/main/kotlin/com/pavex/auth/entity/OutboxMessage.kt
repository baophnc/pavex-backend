package com.pavex.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "outbox_message",
    schema = "auth",
)
class OutboxMessage(

    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(
        name = "aggregate_type",
        length = 80,
    )
    var aggregateType: String? = null,

    @Column(name = "aggregate_id")
    var aggregateId: UUID? = null,

    @Column(
        name = "event_type",
        nullable = false,
        length = 160,
    )
    var eventType: String = "",

    @Column(
        name = "event_version",
        nullable = false,
    )
    var eventVersion: Int = 1,

    @Column(
        name = "routing_key",
        nullable = false,
        length = 160,
    )
    var routingKey: String = "",

    @Column(
        name = "payload_ciphertext",
        nullable = false,
        columnDefinition = "text",
    )
    var payloadCiphertext: String = "",

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 16,
    )
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(
        name = "next_attempt_at",
        nullable = false,
    )
    var nextAttemptAt: Instant = Instant.now(),

    @Column(
        name = "occurred_at",
        nullable = false,
    )
    var occurredAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(
        name = "last_error",
        length = 1000,
    )
    var lastError: String? = null,

    @Column(
        name = "created_at",
        nullable = false,
    )
    var createdAt: Instant = Instant.now(),
)

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    DEAD,
}
