package com.pavex.shipment.dispatch

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ShipmentMessagingConfig {
    /** Shared with the other services; declaring it here is idempotent. */
    @Bean
    fun shipmentEventsExchange(): TopicExchange = TopicExchange(EVENTS_EXCHANGE, true, false)

    companion object {
        const val EVENTS_EXCHANGE = "pavex.events"
    }
}
