package com.pavex.operations.dispatch

import com.pavex.operations.config.OperationsDispatchConfig
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Date
import java.util.concurrent.TimeUnit

@Service
class RabbitOperationsEventPublisher(
    private val rabbitTemplate: RabbitTemplate,
    @Value("\${operations.messaging.exchange:${OperationsDispatchConfig.EVENTS_EXCHANGE}}")
    private val exchange: String,
    @Value("\${operations.outbox.confirm-timeout:PT5S}")
    private val confirmTimeout: Duration,
) {
    fun publish(event: OperationsIntegrationEvent) {
        val correlation = CorrelationData(event.messageId.toString())
        rabbitTemplate.convertAndSend(
            exchange,
            event.eventType,
            event.payload,
            { message ->
                message.messageProperties.apply {
                    messageId = event.messageId.toString()
                    contentType = "application/json"
                    contentEncoding = "UTF-8"
                    timestamp = Date.from(event.occurredAt)
                    setHeader("X-Pavex-Event-Id", event.messageId.toString())
                    setHeader("X-Pavex-Event-Type", event.eventType)
                    setHeader("X-Pavex-Producer", "operations-service")
                    setHeader("X-Pavex-Aggregate-Type", event.aggregateType)
                    setHeader("X-Pavex-Aggregate-Id", event.aggregateId.toString())
                    setHeader("X-Pavex-Attempt", event.attemptCount)
                    setHeader("X-Pavex-Occurred-At", event.occurredAt.toString())
                }
                message
            },
            correlation,
        )

        val confirm = correlation.future.get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!confirm.ack()) {
            throw IllegalStateException(
                "RabbitMQ publisher nack for operations outbox message ${event.messageId}: ${confirm.reason() ?: "unknown"}",
            )
        }
    }
}
