package com.pavex.shipment.service

import com.pavex.shipment.repository.ShipmentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class ShipmentNetworkConfirmationJob(
    private val shipments: ShipmentRepository,
    private val confirmations: ShipmentNetworkConfirmationService,
    @Value("\${shipment.network-confirmation.lookback:PT24H}")
    private val lookback: Duration,
    @Value("\${shipment.network-confirmation.batch-size:50}")
    private val batchSize: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${shipment.network-confirmation.poll-interval:PT30S}",
        initialDelayString = "\${shipment.network-confirmation.initial-delay:PT30S}",
    )
    fun confirmPending() {
        val pending = shipments.findPendingNetworkConfirmations(
            Instant.now().minus(lookback),
            PageRequest.of(0, batchSize),
        )
        for (shipment in pending) {
            try {
                confirmations.confirm(shipment.id, shipment.routeRevision, shipment.createdByUserId)
            } catch (exception: RuntimeException) {
                // Network is probably down; stop this round instead of hammering it.
                logger.warn("Network reservation confirmation retry failed; next attempt next round", exception)
                return
            }
        }
    }
}
