package com.pavex.shipment.dispatch

import com.pavex.outbox.OutboxRabbitMessage
import com.pavex.outbox.RabbitOutboxPublisher
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RabbitShipmentEventPublisher(
    rabbitTemplate: RabbitTemplate,
    @Value("\${shipment.messaging.exchange:${ShipmentMessagingConfig.EVENTS_EXCHANGE}}")
    private val exchange: String,
    @Value("\${shipment.outbox.confirm-timeout:PT5S}")
    confirmTimeout: Duration,
) {
    private val rabbit = RabbitOutboxPublisher(rabbitTemplate, confirmTimeout, "shipment outbox event")

    fun publish(event: ShipmentOutboxDelivery) {
        rabbit.publish(
            OutboxRabbitMessage(
                exchange = exchange,
                routingKey = event.eventType,
                body = event.payload,
                messageId = event.id.toString(),
                contentType = "application/json",
                occurredAt = event.occurredAt,
                headers = linkedMapOf(
                    "X-Pavex-Event-Id" to event.id.toString(),
                    "X-Pavex-Event-Type" to event.eventType,
                    "X-Pavex-Event-Version" to event.eventVersion,
                    "X-Pavex-Producer" to "shipment-service",
                    "X-Pavex-Aggregate-Type" to "Shipment",
                    "X-Pavex-Aggregate-Id" to event.aggregateId.toString(),
                    "X-Pavex-Aggregate-Code" to event.aggregateCode,
                    "X-Pavex-Attempt" to event.attempts,
                    "X-Pavex-Occurred-At" to event.occurredAt.toString(),
                ),
            ),
        )
    }
}
