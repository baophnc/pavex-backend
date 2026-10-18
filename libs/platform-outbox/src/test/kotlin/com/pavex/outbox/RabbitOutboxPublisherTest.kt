package com.pavex.outbox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.core.ReturnedMessage
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Duration
import java.time.Instant
import java.util.Date

class RabbitOutboxPublisherTest {
    private val rabbit = mock(RabbitTemplate::class.java)
    private val sent = mutableListOf<Message>()
    private val message = OutboxRabbitMessage(
        exchange = "pavex.shipment.events",
        routingKey = "shipment.closed",
        body = "{}",
        messageId = "7c0f6c39-8d69-4a8e-8a36-2c1f1d1d5b0e",
        contentType = "application/json",
        occurredAt = Instant.parse("2026-10-18T08:00:00Z"),
        headers = linkedMapOf("X-Pavex-Event-Type" to "shipment.closed", "X-Pavex-Attempt" to 2),
    )

    @Test
    fun `confirmed publish carries the row's id, time and headers`() {
        broker(ack = true)
        RabbitOutboxPublisher(rabbit, Duration.ofSeconds(5), "shipment outbox event").publish(message)

        val properties = sent.single().messageProperties
        assertEquals(message.messageId, properties.messageId)
        assertEquals("application/json", properties.contentType)
        assertEquals("UTF-8", properties.contentEncoding)
        assertEquals(Date.from(message.occurredAt), properties.timestamp)
        assertEquals("shipment.closed", properties.getHeader("X-Pavex-Event-Type"))
        assertEquals(2, properties.getHeader<Int>("X-Pavex-Attempt"))
    }

    @Test
    fun `nack fails the publish`() {
        broker(ack = false, reason = "queue full")
        val failure = assertThrows(IllegalStateException::class.java) {
            RabbitOutboxPublisher(rabbit, Duration.ofSeconds(5), "shipment outbox event").publish(message)
        }
        assertEquals("RabbitMQ publisher nack for shipment outbox event ${message.messageId}: queue full", failure.message)
    }

    @Test
    fun `an unroutable return fails the publish only when the service asks for it`() {
        broker(ack = true, returned = true)
        RabbitOutboxPublisher(rabbit, Duration.ofSeconds(5), "shipment outbox event").publish(message)

        val failure = assertThrows(IllegalStateException::class.java) {
            RabbitOutboxPublisher(rabbit, Duration.ofSeconds(5), "outbox message", rejectReturned = true).publish(message)
        }
        assertEquals("RabbitMQ returned unroutable outbox message ${message.messageId}: NO_ROUTE", failure.message)
    }

    private fun broker(ack: Boolean, reason: String? = null, returned: Boolean = false) {
        doAnswer { invocation ->
            val postProcessor = invocation.getArgument<MessagePostProcessor>(3)
            sent += postProcessor.postProcessMessage(Message("{}".toByteArray(), MessageProperties()))
            val correlation = invocation.getArgument<CorrelationData>(4)
            if (returned) correlation.setReturned(ReturnedMessage(sent.last(), 312, "NO_ROUTE", "x", "y"))
            correlation.future.complete(CorrelationData.Confirm(ack, reason))
            null
        }.`when`(rabbit).convertAndSend(anyString(), anyString(), any(Any::class.java), any(MessagePostProcessor::class.java), any(CorrelationData::class.java))
    }
}
