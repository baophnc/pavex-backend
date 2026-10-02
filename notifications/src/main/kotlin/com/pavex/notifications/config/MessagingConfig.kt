package com.pavex.notifications.config

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Events that become notices. Messages that keep failing are dead-lettered to "<queue>.dlq". */
@Configuration
class MessagingConfig(
    @Value("\${notifications.messaging.exchange:pavex.events}") private val exchange: String,
    @Value("\${notifications.messaging.queue:notifications.events}") private val queue: String,
) {
    @Bean
    fun notificationEventDeclarables(): Declarables {
        val events = TopicExchange(exchange, true, false)
        val deadLetters = QueueBuilder.durable("$queue.dlq").build()
        val notificationEvents = QueueBuilder.durable(queue)
            .deadLetterExchange("")
            .deadLetterRoutingKey("$queue.dlq")
            .build()
        val bindings = SUBSCRIBED_EVENTS.map { BindingBuilder.bind(notificationEvents).to(events).with(it) }
        return Declarables(listOf(events, deadLetters, notificationEvents) + bindings)
    }

    companion object {
        val SUBSCRIBED_EVENTS = listOf(
            "shipment.created",
            "shipment.parcel-scanned",
            "shipment.delivery-attempt-recorded",
            "shipment.cancelled",
            "shipment.return.requested",
            "shipment.forward.requested",
            "shipment.cancellation.requested",
            "shipment.case.created",
            "shipment.case.assigned",
            "operations.assignment.assigned",
            "operations.assignment.reassigned",
            "operations.assignment.requeued",
            "operations.assignment.cancelled",
            "billing.cash.remittance-submitted",
        )
    }
}
