package com.pavex.billing.messaging

import java.time.Instant
import java.util.UUID

/** shipment.cash-collected v1, published by shipment-service. A counter shipment has no merchant. */
data class CashCollectedEvent(
    val shipmentId: UUID,
    val trackingCode: String,
    val merchantId: UUID?,
    val channel: String = MERCHANT_WEB,
    val courierUserId: UUID,
    val hubId: UUID,
    val attemptId: UUID,
    val amount: Long,
    val codAmount: Long,
    val recipientShippingFee: Long,
    val collectedAt: Instant,
)

/** shipment.closed v1: the final money facts of a shipment, published once when it closes. */
data class ShipmentClosedEvent(
    val shipmentId: UUID,
    val trackingCode: String,
    val merchantId: UUID?,
    val channel: String = MERCHANT_WEB,
    val lifecycleStatus: String,
    val finalStatus: String,
    val shippingFeePayer: String,
    val shippingFee: Long,
    val merchantShippingFee: Long,
    /** Unpaid fee a walk-in sender pays at the counter (a returned shipment the recipient was to pay). */
    val counterSenderFeeDue: Long = 0,
    val codAmount: Long,
    val amountToCollect: Long,
    val collectedAmount: Long?,
    val closedAt: Instant,
)

/** shipment.counter-accepted v1: a walk-in shipment booked at a hub counter. */
data class CounterAcceptedEvent(
    val shipmentId: UUID,
    val trackingCode: String,
    val hubId: UUID,
    val clerkUserId: UUID,
    val senderName: String,
    val senderPhone: String,
    val shippingFeePayer: String,
    val shippingFee: Long,
    val feePaidAtCounter: Long,
    val codAmount: Long,
    val acceptedAt: Instant,
)

const val MERCHANT_WEB = "MERCHANT_WEB"
const val COUNTER = "COUNTER"
