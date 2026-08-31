package com.pavex.shipment.dto

import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.DeliveryFailureReason
import com.pavex.shipment.entity.DeliveryProofMethod
import jakarta.validation.constraints.NotBlank
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
)

data class DeliveryAttemptRecordedResponse(
    val attempt: DeliveryAttemptResponse,
    val shipment: ShipmentDetailResponse,
)
