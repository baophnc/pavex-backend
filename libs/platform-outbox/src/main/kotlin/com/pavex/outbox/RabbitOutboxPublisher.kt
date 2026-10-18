package com.pavex.outbox

import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit

/** One outbox row as a RabbitMQ message. [headers] are the service's own `X-Pavex-*` headers. */
data class OutboxRabbitMessage(
    val exchange: String,
    val routingKey: String,
    val body: String,
    val messageId: String,
    val contentType: String,
    val occurredAt: Instant,
    val headers: Map<String, Any>,
)

/**
 * Publishes an outbox row and waits up to [confirmTimeout] for the broker's publisher confirm; a nack
 * (and, with [rejectReturned], an unroutable return) throws, so the relay retries the row.
 * [label] names the row in those errors, e.g. "shipment outbox event".
 */
class RabbitOutboxPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val confirmTimeout: Duration,
    private val label: String,
    private val rejectReturned: Boolean = false,
) {
    fun publish(message: OutboxRabbitMessage) {
        val correlation = CorrelationData(message.messageId)
        rabbitTemplate.convertAndSend(
            message.exchange,
            message.routingKey,
            message.body,
            { rabbitMessage ->
                rabbitMessage.messageProperties.apply {
                    messageId = message.messageId
                    contentType = message.contentType
                    contentEncoding = "UTF-8"
                    timestamp = Date.from(message.occurredAt)
                    message.headers.forEach { (name, value) -> setHeader(name, value) }
                }
                rabbitMessage
            },
            correlation,
        )

        val confirm = correlation.future.get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!confirm.ack()) {
            throw IllegalStateException(
                "RabbitMQ publisher nack for $label ${message.messageId}: ${confirm.reason() ?: "unknown"}",
            )
        }
        val returned = correlation.returned
        if (rejectReturned && returned != null) {
            throw IllegalStateException("RabbitMQ returned unroutable $label ${message.messageId}: ${returned.replyText}")
        }
    }
}
