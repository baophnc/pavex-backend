package com.pavex.shipment.dto

import com.pavex.shipment.entity.ShipmentExceptionRequestKind
import com.pavex.shipment.entity.ShipmentExceptionRequestStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ShipmentHoldRequest(
    @field:NotBlank @field:Size(max = 1000) val reason: String,
    val hubId: UUID? = null,
)

data class ShipmentExceptionRequestBody(
    @field:NotBlank @field:Size(max = 1000) val reason: String,
    val hubId: UUID? = null,
)

data class ShipmentForwardRequestBody(
    @field:NotBlank @field:Size(max = 1000) val reason: String,
    @field:Valid @field:NotNull val recipient: ForwardRecipientRequest,
    val hubId: UUID? = null,
)

data class ForwardRecipientRequest(
    @field:NotBlank @field:Size(max = 120) val contactName: String,
    @field:NotBlank @field:Size(max = 20) val contactPhone: String,
    @field:NotBlank @field:Size(max = 500) val addressLine: String,
    @field:NotBlank @field:Size(max = 20) val provinceCode: String,
    @field:NotBlank @field:Size(max = 20) val wardCode: String,
)

data class ShipmentExceptionReviewRequest(
    val approve: Boolean,
    @field:Size(max = 500) val note: String? = null,
)

data class CompleteShipmentExceptionRequest(
    @field:Size(max = 500) val note: String? = null,
)

data class ShipmentWeightAdjustmentRequest(
    val parcelId: UUID,
    @field:DecimalMin(value = "0.001") val actualWeightKg: BigDecimal,
    @field:NotBlank @field:Size(max = 1000) val reason: String,
    val hubId: UUID? = null,
)

data class ShipmentWeightReviewRequest(
    @field:NotBlank @field:Size(max = 500) val note: String,
)

data class ForwardRecipientResponse(
    val contactName: String,
    val contactPhone: String,
    val addressLine: String,
    val provinceCode: String,
    val wardCode: String,
)

data class ShipmentExceptionRequestResponse(
    val requestId: UUID,
    val kind: ShipmentExceptionRequestKind,
    val status: ShipmentExceptionRequestStatus,
    val reason: String,
    val hubId: UUID?,
    val requestedByUserId: UUID,
    val requestedAt: Instant,
    val reviewedByUserId: UUID?,
    val reviewedAt: Instant?,
    val reviewNote: String?,
    val routingRequired: Boolean,
    val forwardRecipient: ForwardRecipientResponse?,
    val completedByUserId: UUID?,
    val completedAt: Instant?,
    val version: Long,
)

data class ShipmentWeightAdjustmentResponse(
    val adjustmentId: UUID,
    val parcelId: UUID,
    val oldActualWeightKg: BigDecimal,
    val newActualWeightKg: BigDecimal,
    val oldChargeableWeightKg: BigDecimal,
    val newChargeableWeightKg: BigDecimal,
    val reason: String,
    val actorUserId: UUID,
    val hubId: UUID?,
    val adjustedAt: Instant,
    val reviewCompleted: Boolean,
    val reviewedByUserId: UUID?,
    val reviewedAt: Instant?,
    val reviewNote: String?,
)

data class ShipmentExceptionStateResponse(
    val shipmentId: UUID,
    val trackingCode: String,
    val isOnHold: Boolean,
    val holdReason: String?,
    val holdActorUserId: UUID?,
    val holdHubId: UUID?,
    val heldAt: Instant?,
    val hasPendingWeightReview: Boolean,
    val returnRequest: ShipmentExceptionRequestResponse?,
    val forwardRequest: ShipmentExceptionRequestResponse?,
    val cancellationRequest: ShipmentExceptionRequestResponse?,
    val weightAdjustments: List<ShipmentWeightAdjustmentResponse>,
)
