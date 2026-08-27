package com.pavex.identity.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "consumed_message",
    schema = "identity",
    uniqueConstraints = [
        UniqueConstraint(
            name = "ux_consumed_message_consumer",
            columnNames = [
                "message_id",
                "consumer",
            ],
        ),
    ],
)
class ConsumedMessage(

    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(
        name = "message_id",
        nullable = false,
    )
    var messageId: UUID = UUID.randomUUID(),

    @Column(
        nullable = false,
        length = 128,
    )
    var consumer: String = "",

    @Column(
        name = "processed_at",
        nullable = false,
    )
    var processedAt: Instant = Instant.now(),
)
