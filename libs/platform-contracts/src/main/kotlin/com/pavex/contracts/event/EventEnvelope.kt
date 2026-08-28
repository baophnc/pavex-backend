package com.pavex.contracts.event

import java.time.Instant
import java.util.UUID

data class EventEnvelope<T>(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val producer: String,
    val correlationId: String? = null,
    val causationId: String? = null,
    val payload: T,
) {
    init {
        require(eventType.isNotBlank()) {
            "eventType must not be blank"
        }

        require(eventVersion > 0) {
            "eventVersion must be greater than 0"
        }

        require(producer.isNotBlank()) {
            "producer must not be blank"
        }
    }
}
