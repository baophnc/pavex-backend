package com.pavex.shipment.dispatch

import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Date
import java.util.concurrent.TimeUnit

@Service
class RabbitShipmentEventPublisher(
    private val rabbitTemplate: RabbitTemplate,
    @Value("\${shipment.messaging.exchange:${ShipmentMessagingConfig.EVENTS_EXCHANGE}}")
    private val exchange: String,
    @Value("\${shipment.outbox.confirm-timeout:PT5S}")
    private val confirmTimeout: Duration,
) {
    fun publish(event: ShipmentOutboxDelivery) {
        val correlation = CorrelationData(event.id.toString())
        rabbitTemplate.convertAndSend(
            exchange,
            event.eventType,
            event.payload,
            { message ->
                message.messageProperties.apply {
                    messageId = event.id.toString()
                    contentType = "application/json"
                    contentEncoding = "UTF-8"
                    timestamp = Date.from(event.occurredAt)
                    setHeader("X-Pavex-Event-Id", event.id.toString())
                    setHeader("X-Pavex-Event-Type", event.eventType)
                    setHeader("X-Pavex-Event-Version", event.eventVersion)
                    setHeader("X-Pavex-Producer", "shipment-service")
                    setHeader("X-Pavex-Aggregate-Type", "Shipment")
                    setHeader("X-Pavex-Aggregate-Id", event.aggregateId.toString())
                    setHeader("X-Pavex-Aggregate-Code", event.aggregateCode)
                    setHeader("X-Pavex-Attempt", event.attempts)
                    setHeader("X-Pavex-Occurred-At", event.occurredAt.toString())
                }
                message
            },
            correlation,
        )

        val confirm = correlation.future.get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!confirm.ack()) {
            throw IllegalStateException(
                "RabbitMQ publisher nack for shipment outbox event ${event.id}: ${confirm.reason() ?: "unknown"}",
            )
        }
    }
}
