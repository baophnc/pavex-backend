package com.pavex.operations.dispatch

import java.time.Instant
import java.util.UUID

data class OperationsIntegrationEvent(
    val messageId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val payload: String,
    val occurredAt: Instant,
    val attemptCount: Int,
)

data class OperationsOutboxDelivery(
    val messageId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val payload: String,
    val occurredAt: Instant,
    val attemptCount: Int,
)
