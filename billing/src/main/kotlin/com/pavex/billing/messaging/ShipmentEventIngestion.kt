package com.pavex.billing.messaging

import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.MerchantFeeCharge
import com.pavex.billing.entity.ProcessedEvent
import com.pavex.billing.repository.CashCollectionRepository
import com.pavex.billing.repository.MerchantFeeChargeRepository
import com.pavex.billing.repository.ProcessedEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Turns shipment events into ledger rows. Each event is applied once: its id is recorded in the
 * same transaction, and the rows themselves are unique per shipment.
 */
@Service
class ShipmentEventIngestion(
    private val processedEvents: ProcessedEventRepository,
    private val collections: CashCollectionRepository,
    private val feeCharges: MerchantFeeChargeRepository,
    private val clock: Clock,
) {
    @Transactional
    fun cashCollected(eventId: UUID, event: CashCollectedEvent): Boolean {
        if (!markProcessed(eventId, CASH_COLLECTED)) return false
        require(event.amount == event.codAmount + event.recipientShippingFee) {
            "Cash-collected event ${event.trackingCode} amounts do not add up."
        }
        if (collections.existsByShipmentId(event.shipmentId)) {
            log.warn("Cash collection for shipment {} already recorded; ignoring event {}.", event.trackingCode, eventId)
            return false
        }
        collections.save(
            CashCollection.fromDelivery(
                shipmentId = event.shipmentId,
                trackingCode = event.trackingCode,
                merchantId = event.merchantId,
                deliveryAttemptId = event.attemptId,
                courierUserId = event.courierUserId,
                hubId = event.hubId,
                codAmount = event.codAmount,
                feeAmount = event.recipientShippingFee,
                collectedAt = event.collectedAt,
            ),
        )
        return true
    }

    @Transactional
    fun shipmentClosed(eventId: UUID, event: ShipmentClosedEvent): Boolean {
        if (!markProcessed(eventId, SHIPMENT_CLOSED)) return false
        if (event.merchantShippingFee <= 0 || feeCharges.existsByShipmentId(event.shipmentId)) return false
        feeCharges.save(
            MerchantFeeCharge(
                shipmentId = event.shipmentId,
                trackingCode = event.trackingCode.trim().uppercase(),
                merchantId = event.merchantId,
                amount = event.merchantShippingFee,
                shippingFeePayer = event.shippingFeePayer,
                finalStatus = event.finalStatus,
                closedAt = event.closedAt,
                createdAt = clock.instant(),
            ),
        )
        return true
    }

    private fun markProcessed(eventId: UUID, eventType: String): Boolean {
        if (processedEvents.existsById(eventId)) return false
        processedEvents.save(ProcessedEvent(eventId = eventId, eventType = eventType, processedAt = clock.instant()))
        return true
    }

    companion object {
        const val CASH_COLLECTED = "shipment.cash-collected"
        const val SHIPMENT_CLOSED = "shipment.closed"
        private val log = LoggerFactory.getLogger(ShipmentEventIngestion::class.java)
    }
}
