package com.pavex.fleet.messaging

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.pavex.fleet.config.MessagingConfig
import com.pavex.fleet.exception.FleetUnavailableException
import com.pavex.fleet.service.DriverTripService
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParcelScannedEvent(
    val currentStatus: String,
    val routeLegId: UUID? = null,
    val actorUserId: UUID,
)

/**
 * A parcel scanned onto a truck starts the trip of its lane departure for the driver who loaded it,
 * so every run is recorded even when the driver never opens the trips tab.
 */
@Component
class ShipmentEventListener(
    private val driverTrips: DriverTripService,
    private val objectMapper: ObjectMapper,
) {
    @RabbitListener(queues = ["\${fleet.messaging.shipment-queue:fleet.shipment-events}"])
    fun onShipmentEvent(message: Message) {
        val properties = message.messageProperties
        val eventType = properties.getHeader<String>("X-Pavex-Event-Type") ?: properties.receivedRoutingKey
        if (eventType != MessagingConfig.PARCEL_SCANNED) return
        val event = objectMapper.readValue(message.body, ParcelScannedEvent::class.java)
        val routeLegId = event.routeLegId
        if (event.currentStatus != IN_TRANSIT || routeLegId == null) return
        try {
            driverTrips.startFromDeparture(event.actorUserId, routeLegId)
                ?.let { log.info("Trip {} {} → {} is running.", it.id, it.fromHubCode, it.toHubCode) }
        } catch (exception: FleetUnavailableException) {
            // Network did not answer: let the broker retry.
            throw IllegalStateException("Route leg $routeLegId could not be read: ${exception.code}", exception)
        }
    }

    companion object {
        private const val IN_TRANSIT = "IN_TRANSIT"
        private val log = LoggerFactory.getLogger(ShipmentEventListener::class.java)
    }
}
