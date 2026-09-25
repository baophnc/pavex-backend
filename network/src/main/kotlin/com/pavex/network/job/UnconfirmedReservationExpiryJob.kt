package com.pavex.network.job

import com.pavex.network.service.NetworkCapacityService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Releases shipment capacity reservations whose shipment was never persisted
 * (for example Shipment crashed or timed out between reserve and persist).
 * Each reservation is released in its own transaction.
 */
@Component
class UnconfirmedReservationExpiryJob(
    private val capacity: NetworkCapacityService,
    @Value("\${network.capacity.expiry-batch-size:50}")
    private val batchSize: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${network.capacity.expiry-poll-interval:PT1M}",
        initialDelayString = "\${network.capacity.expiry-initial-delay:PT1M}",
    )
    fun releaseExpired() {
        val now = Instant.now()
        val expired = capacity.findExpiredUnconfirmedReservations(now, batchSize)
        var released = 0
        expired.forEach { reservation ->
            try {
                if (capacity.releaseIfExpiredUnconfirmed(reservation.shipmentId, reservation.routeRevision, now)) {
                    released += 1
                }
            } catch (exception: RuntimeException) {
                logger.warn(
                    "Could not expire capacity reservation for shipment {} revision {}",
                    reservation.shipmentId,
                    reservation.routeRevision,
                    exception,
                )
            }
        }
        if (released > 0) {
            logger.warn("Released {} unconfirmed shipment capacity reservation(s)", released)
        }
    }
}
