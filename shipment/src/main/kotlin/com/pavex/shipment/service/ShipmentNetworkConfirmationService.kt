package com.pavex.shipment.service

import com.pavex.shipment.client.NetworkConfirmationResult
import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.repository.ShipmentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * Confirms Network capacity reservations for persisted shipments.
 *
 * Network releases shipment reservations that are not confirmed before their deadline,
 * which protects capacity when Shipment dies between reserve and persist. The HTTP call
 * happens outside any database transaction; only the local bookkeeping is transactional.
 */
@Service
class ShipmentNetworkConfirmationService(
    private val network: NetworkShipmentClient,
    private val shipments: ShipmentRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Never throws: a failure here is retried by [ShipmentNetworkConfirmationJob]. */
    fun confirmBestEffort(shipmentId: UUID, routeRevision: Int, actorUserId: UUID) {
        try {
            confirm(shipmentId, routeRevision, actorUserId)
        } catch (exception: RuntimeException) {
            logger.warn(
                "Network reservation confirmation for shipment {} failed; it will be retried",
                shipmentId,
                exception,
            )
        }
    }

    /** @return true when the shipment no longer needs confirmation. */
    fun confirm(shipmentId: UUID, routeRevision: Int, actorUserId: UUID): Boolean =
        when (network.confirm(shipmentId, routeRevision, actorUserId)) {
            NetworkConfirmationResult.CONFIRMED -> {
                transactions.executeWithoutResult { shipments.markNetworkConfirmed(shipmentId, Instant.now()) }
                true
            }
            NetworkConfirmationResult.ALREADY_RELEASED -> {
                recordTerminalFailure(shipmentId, "capacity_already_released")
                true
            }
            NetworkConfirmationResult.NOT_FOUND -> {
                recordTerminalFailure(shipmentId, "capacity_reservation_not_found")
                true
            }
        }

    private fun recordTerminalFailure(shipmentId: UUID, code: String) {
        // The shipment exists but Network no longer holds its capacity. This needs an
        // operator (re-plan the route); retrying the confirmation cannot fix it.
        logger.error("Network capacity for shipment {} is lost ({}); manual re-planning required", shipmentId, code)
        transactions.executeWithoutResult { shipments.markNetworkConfirmationFailed(shipmentId, code) }
    }
}
