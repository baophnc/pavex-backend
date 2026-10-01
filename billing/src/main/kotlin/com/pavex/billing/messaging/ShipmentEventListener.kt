package com.pavex.billing.messaging

import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Consumes shipment events from billing's queue; failures are retried, then dead-lettered. */
@Component
class ShipmentEventListener(
    private val ingestion: ShipmentEventIngestion,
    private val objectMapper: ObjectMapper,
) {
    @RabbitListener(queues = ["\${billing.messaging.shipment-queue:billing.shipment-events}"])
    fun onShipmentEvent(message: Message) {
        val properties = message.messageProperties
        val eventType = properties.getHeader<String>("X-Pavex-Event-Type") ?: properties.receivedRoutingKey
        val eventId = (properties.getHeader<String>("X-Pavex-Event-Id") ?: properties.messageId)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw IllegalArgumentException("Shipment event without a valid X-Pavex-Event-Id.")
        val body = message.body

        val applied = when (eventType) {
            ShipmentEventIngestion.CASH_COLLECTED ->
                ingestion.cashCollected(eventId, objectMapper.readValue(body, CashCollectedEvent::class.java))
            ShipmentEventIngestion.SHIPMENT_CLOSED ->
                ingestion.shipmentClosed(eventId, objectMapper.readValue(body, ShipmentClosedEvent::class.java))
            ShipmentEventIngestion.COUNTER_ACCEPTED ->
                ingestion.counterAccepted(eventId, objectMapper.readValue(body, CounterAcceptedEvent::class.java))
            else -> {
                log.debug("Ignoring shipment event {} of type {}.", eventId, eventType)
                false
            }
        }
        if (applied) log.info("Applied shipment event {} ({}).", eventId, eventType)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShipmentEventListener::class.java)
    }
}
