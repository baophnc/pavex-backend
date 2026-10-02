package com.pavex.notifications.messaging

import com.pavex.notifications.service.EventNotifications
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Reads events from the notifications queue; failures are retried, then dead-lettered. */
@Component
class NotificationEventListener(
    private val notices: EventNotifications,
    private val objectMapper: ObjectMapper,
) {
    @RabbitListener(queues = ["\${notifications.messaging.queue:notifications.events}"])
    fun onEvent(message: Message) {
        val properties = message.messageProperties
        val eventType = properties.getHeader<String>("X-Pavex-Event-Type") ?: properties.receivedRoutingKey ?: return
        val event = objectMapper.readTree(message.body)
        when {
            eventType == "shipment.created" -> notices.shipmentCreated(event)
            eventType == "shipment.parcel-scanned" -> notices.parcelScanned(event)
            eventType == "shipment.delivery-attempt-recorded" -> notices.deliveryAttempt(event)
            eventType == "shipment.cancelled" -> notices.shipmentCancelled(event)
            eventType.endsWith(".requested") -> notices.exceptionRequested(eventType, event)
            eventType.startsWith("shipment.case.") -> notices.caseEvent(eventType, event)
            eventType.startsWith("operations.assignment.") -> notices.assignment(eventType, event)
            eventType == "billing.cash.remittance-submitted" -> notices.cashSubmitted(event)
            else -> log.debug("Ignoring event {}", eventType)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NotificationEventListener::class.java)
    }
}
