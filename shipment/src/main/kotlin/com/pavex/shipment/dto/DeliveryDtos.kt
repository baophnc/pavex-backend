package com.pavex.shipment.dto

import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.DeliveryFailureReason
import com.pavex.shipment.entity.DeliveryProofMethod
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentCollectionStatus
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShipmentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RecordDeliveryAttemptRequest(
    val assignmentId: UUID,
    @field:NotBlank
    @field:Size(max = 40)
    val parcelCode: String,
    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val idempotencyKey: String,
    val outcome: DeliveryAttemptOutcome,
    val failureReason: DeliveryFailureReason? = null,
    val proofMethod: DeliveryProofMethod? = null,
    @field:Size(max = 200)
    val recipientName: String? = null,
    @field:Size(max = 500)
    val evidenceReference: String? = null,
    @field:Size(max = 500)
    val note: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val occurredAt: Instant? = null,
    /**
     * Money collected from the recipient on this attempt. Required, and exactly the shipment's
     * amount to collect, on the first successful delivery; otherwise omitted.
     */
    @field:PositiveOrZero
    val collectedAmount: Long? = null,
)

data class DeliveryAttemptResponse(
    val parcelCode: String,
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
    val collectedAmount: Long?,
)

/** A shipment the courier currently holds parcels of, with what to collect at the door. */
data class CourierDeliveryResponse(
    val trackingCode: String,
    val status: ShipmentStatus,
    val serviceLevel: ShipmentServiceLevel,
    val recipient: ShipmentAddressResponse,
    val note: String?,
    val amountToCollect: Long,
    val collectionStatus: ShipmentCollectionStatus,
    val parcelCount: Int,
    val parcels: List<CourierParcelResponse>,
)

data class CourierParcelResponse(
    val parcelCode: String,
    val sequence: Int,
    val status: ParcelStatus,
    val description: String,
    val isFragile: Boolean,
    val assignmentId: UUID?,
)

data class DeliveryAttemptRecordedResponse(
    val attempt: DeliveryAttemptResponse,
    val shipment: ShipmentDetailResponse,
)
