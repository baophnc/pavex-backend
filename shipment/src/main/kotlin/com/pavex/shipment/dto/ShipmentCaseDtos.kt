package com.pavex.shipment.dto

import com.pavex.shipment.entity.ShipmentCaseSeverity
import com.pavex.shipment.entity.ShipmentCaseStatus
import com.pavex.shipment.entity.ShipmentCaseType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateProblemParcelCaseRequest(
    @field:NotNull val shipmentId: UUID,
    val parcelId: UUID? = null,
    @field:NotNull val hubId: UUID,
    @field:NotNull val severity: ShipmentCaseSeverity,
    @field:NotBlank @field:Size(max = 160) val title: String,
    @field:NotBlank @field:Size(max = 2000) val description: String,
)

data class CreateUnidentifiedParcelCaseRequest(
    @field:NotNull val hubId: UUID,
    @field:NotNull val severity: ShipmentCaseSeverity,
    @field:NotBlank @field:Size(max = 160) val title: String,
    @field:NotBlank @field:Size(max = 2000) val description: String,
    @field:Size(max = 120) val externalBarcode: String? = null,
)

data class AssignShipmentCaseRequest(
    @field:NotNull val assignedToUserId: UUID,
    @field:Size(max = 1000) val note: String? = null,
)

data class ShipmentCaseNoteRequest(@field:Size(max = 1000) val note: String? = null)
data class AddShipmentCaseNoteRequest(@field:NotBlank @field:Size(max = 2000) val note: String)
data class LinkShipmentCaseRequest(
    @field:NotNull val shipmentId: UUID,
    val parcelId: UUID? = null,
    @field:Size(max = 1000) val note: String? = null,
)
data class ResolveShipmentCaseRequest(@field:NotBlank @field:Size(max = 2000) val resolution: String)

data class ShipmentCaseEventResponse(
    val id: UUID,
    val code: String,
    val description: String,
    val actorUserId: UUID,
    val occurredAt: Instant,
    val dataJson: String?,
)

data class ShipmentCaseResponse(
    val id: UUID,
    val caseNumber: String,
    val type: ShipmentCaseType,
    val status: ShipmentCaseStatus,
    val severity: ShipmentCaseSeverity,
    val hubId: UUID,
    val shipmentId: UUID?,
    val parcelId: UUID?,
    val externalBarcode: String?,
    val title: String,
    val description: String,
    val reportedByUserId: UUID,
    val assignedToUserId: UUID?,
    val resolution: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?,
    val closedAt: Instant?,
    val version: Long,
    val events: List<ShipmentCaseEventResponse>,
)

data class ShipmentCaseSummaryResponse(
    val id: UUID,
    val caseNumber: String,
    val type: ShipmentCaseType,
    val status: ShipmentCaseStatus,
    val severity: ShipmentCaseSeverity,
    val hubId: UUID,
    val shipmentId: UUID?,
    val parcelId: UUID?,
    val externalBarcode: String?,
    val title: String,
    val assignedToUserId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ShipmentCaseMonitoringSummary(
    val totalActive: Long,
    val open: Long,
    val assigned: Long,
    val investigating: Long,
    val problemParcels: Long,
    val unidentifiedParcels: Long,
    val highOrCritical: Long,
    val resolvedToday: Long,
)
