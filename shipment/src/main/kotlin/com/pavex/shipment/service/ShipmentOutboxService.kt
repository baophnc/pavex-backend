package com.pavex.shipment.service

import com.pavex.shipment.entity.ShipmentOutboxEvent
import com.pavex.shipment.repository.ShipmentOutboxEventRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ShipmentOutboxService(
    private val repository: ShipmentOutboxEventRepository,
) {
    fun enqueue(
        aggregateId: UUID,
        aggregateCode: String,
        eventType: String,
        payloadJson: String,
        occurredAt: Instant,
        eventVersion: Int = 1,
    ): ShipmentOutboxEvent {
        require(eventVersion > 0) { "eventVersion must be positive." }
        require(aggregateCode.isNotBlank()) { "aggregateCode is required." }
        require(eventType.isNotBlank()) { "eventType is required." }
        return repository.save(
            ShipmentOutboxEvent(
                aggregateId = aggregateId,
                aggregateCode = aggregateCode.trim(),
                eventType = eventType.trim(),
                eventVersion = eventVersion,
                payloadJson = payloadJson,
                occurredAt = occurredAt,
                nextAttemptAt = occurredAt,
                createdAt = occurredAt,
            ),
        )
    }
}
