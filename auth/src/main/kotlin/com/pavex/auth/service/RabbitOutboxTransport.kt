package com.pavex.auth.service

import com.pavex.auth.entity.OutboxMessage
import com.pavex.auth.messaging.AuthMessagingTopology
import com.pavex.outbox.OutboxRabbitMessage
import com.pavex.outbox.RabbitOutboxPublisher
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

/** Sends the encrypted payload as stored; an unroutable message counts as a failed publish. */
@Service
class RabbitOutboxTransport(

    rabbitTemplate:
        RabbitTemplate,

    @Value(
        "\${auth.messaging.exchange:${AuthMessagingTopology.EVENTS_EXCHANGE}}"
    )
    private val exchange: String,

    @Value(
        "\${auth.outbox.confirm-timeout:PT5S}"
    )
    confirmTimeout:
        Duration,
) {

    private val rabbit =
        RabbitOutboxPublisher(
            rabbitTemplate,
            confirmTimeout,
            label = "outbox message",
            rejectReturned = true,
        )

    fun publish(
        message: OutboxMessage,
    ) {
        rabbit.publish(
            OutboxRabbitMessage(
                exchange = exchange,
                routingKey = message.routingKey,
                body = message.payloadCiphertext,
                messageId = message.id.toString(),
                contentType = "application/vnd.pavex.encrypted+json",
                occurredAt = message.occurredAt,
                headers = linkedMapOf(
                    AuthMessagingTopology.HEADER_EVENT_ID to message.id.toString(),
                    AuthMessagingTopology.HEADER_EVENT_TYPE to message.eventType,
                    AuthMessagingTopology.HEADER_EVENT_VERSION to message.eventVersion,
                    AuthMessagingTopology.HEADER_PRODUCER to AuthMessagingTopology.PRODUCER,
                    AuthMessagingTopology.HEADER_OCCURRED_AT to message.occurredAt.toString(),
                ),
            ),
        )
    }
}
