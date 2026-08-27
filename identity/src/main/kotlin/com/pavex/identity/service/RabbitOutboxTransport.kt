package com.pavex.identity.service

import com.pavex.identity.entity.OutboxMessage
import com.pavex.identity.messaging.IdentityMessagingTopology
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Date
import java.util.concurrent.TimeUnit

@Service
class RabbitOutboxTransport(

    private val rabbitTemplate:
        RabbitTemplate,

    @Value(
        "\${identity.messaging.exchange:${IdentityMessagingTopology.EVENTS_EXCHANGE}}"
    )
    private val exchange: String,

    @Value(
        "\${identity.outbox.confirm-timeout:PT5S}"
    )
    private val confirmTimeout:
        Duration,
) {

    fun publish(
        message: OutboxMessage,
    ) {
        val correlationData =
            CorrelationData(
                message.id.toString()
            )

        rabbitTemplate.convertAndSend(
            exchange,
            message.routingKey,
            message.payloadCiphertext,
            { rabbitMessage ->
                rabbitMessage
                    .messageProperties
                    .apply {
                        messageId =
                            message.id.toString()

                        contentType =
                            "application/vnd.pavex.encrypted+json"

                        contentEncoding =
                            "UTF-8"

                        timestamp =
                            Date.from(
                                message.occurredAt
                            )

                        setHeader(
                            IdentityMessagingTopology.HEADER_EVENT_ID,
                            message.id.toString(),
                        )

                        setHeader(
                            IdentityMessagingTopology.HEADER_EVENT_TYPE,
                            message.eventType,
                        )

                        setHeader(
                            IdentityMessagingTopology.HEADER_EVENT_VERSION,
                            message.eventVersion,
                        )

                        setHeader(
                            IdentityMessagingTopology.HEADER_PRODUCER,
                            IdentityMessagingTopology.PRODUCER,
                        )

                        setHeader(
                            IdentityMessagingTopology.HEADER_OCCURRED_AT,
                            message.occurredAt.toString(),
                        )
                    }

                rabbitMessage
            },
            correlationData,
        )

        val confirm =
            correlationData
                .future
                .get(
                    confirmTimeout.toMillis(),
                    TimeUnit.MILLISECONDS,
                )

        if (!confirm.ack()) {
            throw IllegalStateException(
                "RabbitMQ publisher nack for outbox message ${message.id}: ${confirm.reason() ?: "unknown"}"
            )
        }

        val returned =
            correlationData.returned

        if (returned != null) {
            throw IllegalStateException(
                "RabbitMQ returned unroutable outbox message ${message.id}: ${returned.replyText}"
            )
        }
    }
}
