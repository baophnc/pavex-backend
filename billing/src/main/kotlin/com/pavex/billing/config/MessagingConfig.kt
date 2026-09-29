package com.pavex.billing.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Billing builds its ledger from the shipment events it subscribes to. Messages that keep
 * failing are dead-lettered to "<queue>.dlq" for inspection rather than retried forever.
 */
@Configuration
class MessagingConfig(
    @Value("\${billing.messaging.exchange:pavex.events}") private val exchange: String,
    @Value("\${billing.messaging.shipment-queue:billing.shipment-events}") private val queue: String,
) {
    @Bean
    fun billingShipmentEventDeclarables(): Declarables {
        val events = TopicExchange(exchange, true, false)
        val deadLetters = QueueBuilder.durable("$queue.dlq").build()
        val shipmentEvents = QueueBuilder.durable(queue)
            .deadLetterExchange("")
            .deadLetterRoutingKey("$queue.dlq")
            .build()
        val bindings: List<Binding> = SUBSCRIBED_EVENTS.map { eventType ->
            BindingBuilder.bind(shipmentEvents).to(events).with(eventType)
        }
        return Declarables(listOf(events, deadLetters, shipmentEvents) + bindings)
    }

    companion object {
        const val CASH_COLLECTED = "shipment.cash-collected"
        const val SHIPMENT_CLOSED = "shipment.closed"
        val SUBSCRIBED_EVENTS = listOf(CASH_COLLECTED, SHIPMENT_CLOSED)
    }
}
