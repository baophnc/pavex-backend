package com.pavex.operations.dispatch

import com.pavex.operations.config.OperationsDispatchConfig
import com.pavex.outbox.OutboxRabbitMessage
import com.pavex.outbox.RabbitOutboxPublisher
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RabbitOperationsEventPublisher(
    rabbitTemplate: RabbitTemplate,
    @Value("\${operations.messaging.exchange:${OperationsDispatchConfig.EVENTS_EXCHANGE}}")
    private val exchange: String,
    @Value("\${operations.outbox.confirm-timeout:PT5S}")
    confirmTimeout: Duration,
) {
    private val rabbit = RabbitOutboxPublisher(rabbitTemplate, confirmTimeout, "operations outbox message")

    fun publish(event: OperationsIntegrationEvent) {
        rabbit.publish(
            OutboxRabbitMessage(
                exchange = exchange,
                routingKey = event.eventType,
                body = event.payload,
                messageId = event.messageId.toString(),
                contentType = "application/json",
                occurredAt = event.occurredAt,
                headers = linkedMapOf(
                    "X-Pavex-Event-Id" to event.messageId.toString(),
                    "X-Pavex-Event-Type" to event.eventType,
                    "X-Pavex-Producer" to "operations-service",
                    "X-Pavex-Aggregate-Type" to event.aggregateType,
                    "X-Pavex-Aggregate-Id" to event.aggregateId.toString(),
                    "X-Pavex-Attempt" to event.attemptCount,
                    "X-Pavex-Occurred-At" to event.occurredAt.toString(),
                ),
            ),
        )
    }
}
