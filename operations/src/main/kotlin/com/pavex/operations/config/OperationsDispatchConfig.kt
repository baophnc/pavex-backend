package com.pavex.operations.config

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class OperationsDispatchConfig {
    @Bean
    fun operationsEventsExchange(): TopicExchange = TopicExchange(EVENTS_EXCHANGE, true, false)

    companion object {
        const val EVENTS_EXCHANGE = "pavex.events"
    }
}
