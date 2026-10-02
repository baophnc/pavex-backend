package com.pavex.fleet.config

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Fleet learns that a truck left from the parcels scanned onto it. Messages that keep failing are
 * dead-lettered to "<queue>.dlq" instead of being retried forever.
 */
@Configuration
class MessagingConfig(
    @Value("\${fleet.messaging.exchange:pavex.events}") private val exchange: String,
    @Value("\${fleet.messaging.shipment-queue:fleet.shipment-events}") private val queue: String,
) {
    @Bean
    fun fleetShipmentEventDeclarables(): Declarables {
        val events = TopicExchange(exchange, true, false)
        val deadLetters = QueueBuilder.durable("$queue.dlq").build()
        val shipmentEvents = QueueBuilder.durable(queue)
            .deadLetterExchange("")
            .deadLetterRoutingKey("$queue.dlq")
            .build()
        val binding = BindingBuilder.bind(shipmentEvents).to(events).with(PARCEL_SCANNED)
        return Declarables(events, deadLetters, shipmentEvents, binding)
    }

    companion object {
        const val PARCEL_SCANNED = "shipment.parcel-scanned"
    }
}
