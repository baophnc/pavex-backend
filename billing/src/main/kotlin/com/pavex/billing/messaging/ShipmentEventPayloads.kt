package com.pavex.billing.messaging

import java.time.Instant
import java.util.UUID

/** shipment.cash-collected v1, published by shipment-service. */
data class CashCollectedEvent(
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

/** shipment.closed v1: the final money facts of a shipment, published once when it closes. */
data class ShipmentClosedEvent(
    val shipmentId: UUID,
    val trackingCode: String,
    val merchantId: UUID,
    val lifecycleStatus: String,
    val finalStatus: String,
    val shippingFeePayer: String,
    val shippingFee: Long,
    val merchantShippingFee: Long,
    val codAmount: Long,
    val amountToCollect: Long,
    val collectedAmount: Long?,
    val closedAt: Instant,
)
