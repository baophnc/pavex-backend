package com.pavex.shipment.dto

import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.DeliveryFailureReason
import com.pavex.shipment.entity.DeliveryProofMethod
import com.pavex.shipment.entity.ShipmentChannel
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShipmentStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AdminShipmentPageResponse(
    val items: List<AdminShipmentSummaryResponse>,
    val nextCursor: String?,
    val totalCount: Long,
)

data class AdminShipmentSummaryResponse(
    val id: UUID,
    val trackingCode: String,
    val merchantId: UUID?,
    val channel: ShipmentChannel,
    val createdByUserId: UUID,
    val merchantReference: String?,
    val status: ShipmentStatus,
    val lifecycleStatus: ShipmentLifecycleStatus,
    val serviceLevel: ShipmentServiceLevel,
    val shippingFee: Long,
    val codAmount: Long,
    val amountToCollect: Long,
    val destinationProvinceCode: String,
    val destinationProvinceName: String,
    val destinationWardCode: String,
    val destinationWardName: String,
    val originHubCode: String,
    val originHubName: String,
    val destinationHubCode: String,
    val destinationHubName: String,
    val parcelCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val cancellationReason: String? = null,
    val cancelledAt: Instant? = null,
    val cancelledAfterPickup: Boolean = false,
)

data class AdminShipmentDetailResponse(
    val id: UUID,
    val merchantId: UUID?,
    val channel: ShipmentChannel,
    /** Cash the walk-in sender paid at the counter for the shipping fee. */
    val counterFeePaidAmount: Long?,
    val createdByUserId: UUID,
    val quoteId: UUID,
    val quoteRequestId: UUID,
    val ratePlanId: UUID,
    val rateRevision: Int,
    val capacityReservationBatchId: UUID?,
    val originServiceAreaId: UUID,
    val originRegionId: UUID,
    val originHubId: UUID,
    val destinationServiceAreaId: UUID,
    val destinationRegionId: UUID,
    val destinationHubId: UUID,
    val version: Long,
    val shipment: ShipmentDetailResponse,
    val events: List<AdminShipmentEventResponse>,
    val deliveryAttempts: List<AdminDeliveryAttemptResponse>,
)

data class AdminShipmentEventResponse(
    val id: UUID,
    val status: ShipmentStatus,
    val code: String,
    val description: String,
    val parcelCode: String?,
    val actorUserId: UUID?,
    val hubId: UUID?,
    val locationName: String?,
    val dataJson: String?,
    val occurredAt: Instant,
    val recordedAt: Instant,
)

data class AdminDeliveryAttemptResponse(
    val id: UUID,
    val parcelCode: String,
    val actorUserId: UUID,
    val attemptNumber: Int,
    val outcome: DeliveryAttemptOutcome,
    val failureReason: DeliveryFailureReason?,
    val proofMethod: DeliveryProofMethod?,
    val recipientName: String?,
    val evidenceReference: String?,
    val note: String?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val occurredAt: Instant,
    val createdAt: Instant,
)
