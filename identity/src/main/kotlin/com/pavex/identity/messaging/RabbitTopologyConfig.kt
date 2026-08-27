package com.pavex.identity.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class RabbitTopologyConfig(

    @Value(
        "\${identity.messaging.exchange:${IdentityMessagingTopology.EVENTS_EXCHANGE}}"
    )
    private val eventsExchangeName: String,
) {

    @Bean
    fun pavexEventsExchange(): TopicExchange =
        TopicExchange(
            eventsExchangeName,
            true,
            false,
        )

    @Bean
    fun pavexDeadLetterExchange(): TopicExchange =
        TopicExchange(
            IdentityMessagingTopology.DEAD_LETTER_EXCHANGE,
            true,
            false,
        )

    @Bean
    fun identityEmailQueue(): Queue =
        QueueBuilder
            .durable(
                IdentityMessagingTopology.EMAIL_QUEUE
            )
            .withArgument(
                "x-dead-letter-exchange",
                IdentityMessagingTopology.DEAD_LETTER_EXCHANGE,
            )
            .withArgument(
                "x-dead-letter-routing-key",
                IdentityMessagingTopology.EMAIL_DEAD_LETTER_ROUTING_KEY,
            )
            .build()

    @Bean
    fun identityEmailDeadLetterQueue(): Queue =
        QueueBuilder
            .durable(
                IdentityMessagingTopology.EMAIL_DEAD_LETTER_QUEUE
            )
            .build()

    @Bean
    fun identityEmailBinding(
        identityEmailQueue: Queue,
        pavexEventsExchange: TopicExchange,
    ): Binding =
        BindingBuilder
            .bind(identityEmailQueue)
            .to(pavexEventsExchange)
            .with(
                IdentityMessagingTopology.EMAIL_ROUTING_PATTERN
            )

    @Bean
    fun identityEmailDeadLetterBinding(
        identityEmailDeadLetterQueue: Queue,
        pavexDeadLetterExchange: TopicExchange,
    ): Binding =
        BindingBuilder
            .bind(identityEmailDeadLetterQueue)
            .to(pavexDeadLetterExchange)
            .with(
                IdentityMessagingTopology.EMAIL_DEAD_LETTER_ROUTING_KEY
            )
}
