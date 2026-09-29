package com.pavex.shipment.service

import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentStatus
import com.pavex.shipment.entity.ShippingFeePayer
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * Integration events that money flows (courier cash, merchant settlement) are built from.
 * Callers enqueue them in the same transaction as the change they describe.
 */
@Component
class ShipmentFinancialEvents(
    private val outbox: ShipmentOutboxService,
    private val objectMapper: ObjectMapper,
) {
    fun cashCollected(shipment: Shipment) {
        val amount = requireNotNull(shipment.collectedAmount) { "Shipment has no recorded collection." }
        val collectedAt = requireNotNull(shipment.collectedAt)
        outbox.enqueue(
            aggregateId = shipment.id,
            aggregateCode = shipment.trackingCode,
            eventType = CASH_COLLECTED,
            payloadJson = objectMapper.writeValueAsString(
                ShipmentCashCollectedPayload(
                    shipmentId = shipment.id,
                    trackingCode = shipment.trackingCode,
                    merchantId = shipment.merchantId,
                    courierUserId = requireNotNull(shipment.collectedByUserId),
                    hubId = shipment.destinationHubId,
                    attemptId = requireNotNull(shipment.collectionAttemptId),
                    amount = amount,
                    codAmount = shipment.codAmount,
                    recipientShippingFee = shipment.recipientShippingFee(),
                    collectedAt = collectedAt,
                ),
            ),
            occurredAt = collectedAt,
        )
    }

    /** Enqueues the closing snapshot when this change moved the shipment out of ACTIVE. */
    fun closedIfChanged(shipment: Shipment, lifecycleBefore: ShipmentLifecycleStatus) {
        if (lifecycleBefore != ShipmentLifecycleStatus.ACTIVE || shipment.lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) {
            return
        }
        val closedAt = shipment.completedAt ?: shipment.cancelledAt ?: shipment.updatedAt
        outbox.enqueue(
            aggregateId = shipment.id,
            aggregateCode = shipment.trackingCode,
            eventType = CLOSED,
            payloadJson = objectMapper.writeValueAsString(
                ShipmentClosedPayload(
                    shipmentId = shipment.id,
                    trackingCode = shipment.trackingCode,
                    merchantId = shipment.merchantId,
                    lifecycleStatus = shipment.lifecycleStatus,
                    finalStatus = shipment.status,
                    shippingFeePayer = shipment.shippingFeePayer,
                    shippingFee = shipment.shippingFee,
                    merchantShippingFee = shipment.merchantShippingFee(),
                    codAmount = shipment.codAmount,
                    amountToCollect = shipment.recipientCollectAmount,
                    collectedAmount = shipment.collectedAmount,
                    closedAt = closedAt,
                ),
            ),
            occurredAt = closedAt,
        )
    }

    companion object {
        const val CASH_COLLECTED = "shipment.cash-collected"
        const val CLOSED = "shipment.closed"
    }
}

data class ShipmentCashCollectedPayload(
    val shipmentId: UUID,
    val trackingCode: String,
    val merchantId: UUID,
    val courierUserId: UUID,
    val hubId: UUID,
    val attemptId: UUID,
    val amount: Long,
    val codAmount: Long,
    val recipientShippingFee: Long,
    val collectedAt: Instant,
)

/** Final money facts: the fee charged to the merchant and what was collected from the recipient. */
data class ShipmentClosedPayload(
    val shipmentId: UUID,
    val trackingCode: String,
    val merchantId: UUID,
    val lifecycleStatus: ShipmentLifecycleStatus,
    val finalStatus: ShipmentStatus,
    val shippingFeePayer: ShippingFeePayer,
    val shippingFee: Long,
    val merchantShippingFee: Long,
    val codAmount: Long,
    val amountToCollect: Long,
    val collectedAmount: Long?,
    val closedAt: Instant,
)
