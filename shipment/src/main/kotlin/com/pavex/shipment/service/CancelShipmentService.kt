package com.pavex.shipment.service

import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentRoutingStatus
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class CancelShipmentService(
    private val store: ShipmentCancellationStore,
    private val network: NetworkShipmentClient,
    private val operations: OperationsShipmentClient,
) {
    fun cancel(actorUserId: UUID, rawTrackingCode: String, reason: String): ShipmentDetailResponse {
        val trackingCode = ShipmentCodeNormalizer.normalizeOrNull(rawTrackingCode)
            ?.takeIf { !it.contains('-') }
            ?: throw notFound()

        val state = store.cancelLocally(actorUserId, trackingCode, reason)
        operations.cancel(state.shipmentId, "Shipment cancelled: ${state.cancellationReason}")
        if (state.routeRevision > 0 && state.routingStatus != ShipmentRoutingStatus.RELEASED) {
            if (state.capacityReservationBatchId != null) {
                network.release(
                    shipmentId = state.shipmentId,
                    routeRevision = state.routeRevision,
                    actorUserId = actorUserId,
                    reason = "Shipment cancelled: ${state.cancellationReason}",
                )
            }
            store.markRouteReleased(state.shipmentId)
        }
        return store.getOwned(actorUserId, trackingCode)
    }

    private fun notFound() = ShipmentNotFoundException("shipment_not_found", "Shipment was not found.")
}

@Service
class ShipmentCancellationStore(
    private val shipments: ShipmentRepository,
    private val outbox: ShipmentOutboxService,
    private val financialEvents: ShipmentFinancialEvents,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun cancelLocally(actorUserId: UUID, trackingCode: String, rawReason: String): ShipmentCancellationState {
        val shipment = shipments.findByTrackingCodeIgnoreCaseAndCreatedByUserId(trackingCode, actorUserId)
            ?: throw ShipmentNotFoundException("shipment_not_found", "Shipment was not found.")
        val reason = rawReason.trim()
        if (reason.isEmpty() || reason.length > 500) {
            throw com.pavex.shipment.exception.InvalidShipmentRequestException(
                "invalid_cancellation_reason",
                "Cancellation reason is required and must not exceed 500 characters.",
            )
        }

        if (shipment.lifecycleStatus != ShipmentLifecycleStatus.CANCELLED) {
            try {
                shipment.cancel(reason, actorUserId, Instant.now())
                shipments.saveAndFlush(shipment)
            } catch (exception: IllegalArgumentException) {
                throw ShipmentConflictException(
                    "shipment_cannot_be_cancelled",
                    exception.message ?: "Shipment cannot be cancelled.",
                )
            } catch (exception: ObjectOptimisticLockingFailureException) {
                throw ShipmentConflictException(
                    "shipment_changed",
                    "Shipment changed while cancellation was being processed.",
                )
            }
            outbox.enqueue(
                aggregateId = shipment.id,
                aggregateCode = shipment.trackingCode,
                eventType = "shipment.cancelled",
                payloadJson = objectMapper.writeValueAsString(
                    ShipmentCancelledOutboxPayload(
                        shipmentId = shipment.id,
                        trackingCode = shipment.trackingCode,
                        merchantId = shipment.merchantId,
                        reason = requireNotNull(shipment.cancellationReason),
                        actorUserId = actorUserId,
                        occurredAt = requireNotNull(shipment.cancelledAt),
                    ),
                ),
                occurredAt = requireNotNull(shipment.cancelledAt),
            )
            financialEvents.closedIfChanged(shipment, ShipmentLifecycleStatus.ACTIVE)
        }

        return shipment.toCancellationState()
    }

    @Transactional
    fun markRouteReleased(shipmentId: UUID) {
        val shipment = shipments.findById(shipmentId).orElseThrow {
            ShipmentNotFoundException("shipment_not_found", "Shipment was not found.")
        }
        if (shipment.routingStatus != ShipmentRoutingStatus.RELEASED) {
            shipment.markRouteReleased(Instant.now())
            shipments.saveAndFlush(shipment)
        }
    }

    @Transactional(readOnly = true)
    fun getOwned(actorUserId: UUID, trackingCode: String): ShipmentDetailResponse {
        val shipment = shipments.findByTrackingCodeIgnoreCaseAndCreatedByUserId(trackingCode, actorUserId)
            ?: throw ShipmentNotFoundException("shipment_not_found", "Shipment was not found.")
        return ShipmentResponseMapper.detail(shipment)
    }

    private fun Shipment.toCancellationState() = ShipmentCancellationState(
        shipmentId = id,
        routeRevision = routeRevision,
        routingStatus = routingStatus,
        capacityReservationBatchId = capacityReservationBatchId,
        cancellationReason = requireNotNull(cancellationReason),
    )
}

data class ShipmentCancellationState(
    val shipmentId: UUID,
    val routeRevision: Int,
    val routingStatus: ShipmentRoutingStatus,
    val capacityReservationBatchId: UUID?,
    val cancellationReason: String,
)

private data class ShipmentCancelledOutboxPayload(
    val shipmentId: UUID,
    val trackingCode: String,
    val merchantId: UUID?,
    val reason: String,
    val actorUserId: UUID,
    val occurredAt: Instant,
)
