package com.pavex.billing.messaging

import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.CashSource
import com.pavex.billing.entity.CounterSettlementKind
import com.pavex.billing.entity.CounterShipment
import com.pavex.billing.entity.MerchantFeeCharge
import com.pavex.billing.entity.ProcessedEvent
import com.pavex.billing.repository.CashCollectionRepository
import com.pavex.billing.repository.MerchantFeeChargeRepository
import com.pavex.billing.repository.ProcessedEventRepository
import com.pavex.billing.repository.CounterShipmentRepository
import com.pavex.billing.service.CounterCashService
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
    private val counterShipments: CounterShipmentRepository,
    private val counterCash: CounterCashService,
    private val clock: Clock,
) {
    @Transactional
    fun cashCollected(eventId: UUID, event: CashCollectedEvent): Boolean {
        if (!markProcessed(eventId, CASH_COLLECTED)) return false
        require(event.amount == event.codAmount + event.recipientShippingFee) {
            "Cash-collected event ${event.trackingCode} amounts do not add up."
        }
        require(event.merchantId != null || event.channel == COUNTER) {
            "Cash-collected event ${event.trackingCode} of a shop shipment has no merchant."
        }
        if (collections.existsByShipmentIdAndSource(event.shipmentId, CashSource.DELIVERY)) {
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
        if (event.channel == COUNTER) {
            // No merchant is charged; a sender who still owes the fee pays it at the counter.
            if (event.counterSenderFeeDue <= 0) return false
            val counter = counterShipments.findById(event.shipmentId).orElseThrow {
                IllegalStateException("Counter shipment ${event.trackingCode} closed before billing learned it was booked.")
            }
            return counterCash.open(counter, CounterSettlementKind.COLLECT_RETURN_FEE, event.counterSenderFeeDue, clock.instant())
        }
        val merchantId = requireNotNull(event.merchantId) { "Closed shop shipment ${event.trackingCode} has no merchant." }
        if (event.merchantShippingFee <= 0 || feeCharges.existsByShipmentId(event.shipmentId)) return false
        feeCharges.save(
            MerchantFeeCharge(
                shipmentId = event.shipmentId,
                trackingCode = event.trackingCode.trim().uppercase(),
                merchantId = merchantId,
                amount = event.merchantShippingFee,
                shippingFeePayer = event.shippingFeePayer,
                finalStatus = event.finalStatus,
                closedAt = event.closedAt,
                createdAt = clock.instant(),
            ),
        )
        return true
    }

    /**
     * Remembers who sent a walk-in shipment, and books the fee the sender paid at the counter as
     * cash the hub holds until it hands it to finance.
     */
    @Transactional
    fun counterAccepted(eventId: UUID, event: CounterAcceptedEvent): Boolean {
        if (!markProcessed(eventId, COUNTER_ACCEPTED)) return false
        if (counterShipments.existsById(event.shipmentId)) return false
        val code = event.trackingCode.trim().uppercase()
        counterShipments.save(
            CounterShipment(
                shipmentId = event.shipmentId,
                trackingCode = code,
                hubId = event.hubId,
                clerkUserId = event.clerkUserId,
                senderName = event.senderName.trim().take(120),
                senderPhone = event.senderPhone.trim(),
                shippingFeePayer = event.shippingFeePayer,
                shippingFee = event.shippingFee,
                feePaidAtCounter = event.feePaidAtCounter,
                codAmount = event.codAmount,
                acceptedAt = event.acceptedAt,
                createdAt = clock.instant(),
            ),
        )
        if (event.feePaidAtCounter > 0 && !collections.existsByShipmentIdAndSource(event.shipmentId, CashSource.COUNTER)) {
            collections.save(
                CashCollection.fromCounter(
                    shipmentId = event.shipmentId,
                    trackingCode = code,
                    clerkUserId = event.clerkUserId,
                    hubId = event.hubId,
                    feeAmount = event.feePaidAtCounter,
                    reference = "CNT-$code",
                    paidAt = event.acceptedAt,
                ),
            )
        }
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
        const val COUNTER_ACCEPTED = "shipment.counter-accepted"
        private val log = LoggerFactory.getLogger(ShipmentEventIngestion::class.java)
    }
}
