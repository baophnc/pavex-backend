package com.pavex.shipment.dto

import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentCollectionStatus
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShipmentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One open job of a field worker, with the shipment facts needed to do it. */
data class WorkforceTaskResponse(
    val assignmentId: UUID,
    val assignmentCode: String,
    val type: OperationsAssignmentType,
    val status: OperationsAssignmentStatus,
    val hubId: UUID,
    val hubName: String?,
    val routeLegId: UUID?,
    val priority: Int,
    val assignedAt: Instant?,
    val acceptedAt: Instant?,
    val startedAt: Instant?,
    val shipment: WorkforceShipmentResponse,
)

data class WorkforceShipmentResponse(
    val trackingCode: String,
    val merchantReference: String?,
    val status: ShipmentStatus,
    val serviceLevel: ShipmentServiceLevel,
    val sender: ShipmentAddressResponse,
    val recipient: ShipmentAddressResponse,
    val originHubId: UUID,
    val originHubName: String,
    val destinationHubId: UUID,
    val destinationHubName: String,
    /** What the courier must collect from the recipient (COD plus a recipient-paid fee). */
    val amountToCollect: Long,
    val codAmount: Long,
    val collectionStatus: ShipmentCollectionStatus,
    val note: String?,
    val parcels: List<WorkforceParcelResponse>,
    val updatedAt: Instant,
)

data class WorkforceParcelResponse(
    val parcelCode: String,
    val sequence: Int,
    val status: ParcelStatus,
    val description: String,
    val isFragile: Boolean,
    val chargeableWeightKg: BigDecimal,
    val currentHubId: UUID?,
    val currentHubName: String?,
)

/** What a worker can do with a scanned parcel or shipment right now. */
enum class WorkforceScanAction {
    /** Courier collects the parcel from the sender. */
    PICK_UP,

    /** Hub staff take the parcel into the hub (from a courier, a truck or a return). */
    RECEIVE,

    /** Line-haul driver loads the parcel for its next leg. */
    DEPART,

    /** Courier takes the parcel out for delivery. */
    OUT_FOR_DELIVERY,

    /** Hub staff confirm the parcel is on the shelf (stock check). */
    CHECK,
}

data class WorkforceScanRequest(
    @field:NotBlank @field:Size(max = 40) val code: String,
    val action: WorkforceScanAction,
    /** The hub the worker is standing in; required for RECEIVE and CHECK. */
    val hubId: UUID? = null,
)

data class WorkforceScanResponse(
    val trackingCode: String,
    val results: List<WorkforceScanResult>,
    val shipment: WorkforceShipmentResponse,
)

data class WorkforceScanResult(
    val parcelCode: String,
    val ok: Boolean,
    val status: ParcelStatus,
    val code: String?,
    val message: String,
    /** The assignment to accept or start when [code] asks for it. */
    val assignmentId: UUID? = null,
)

data class WorkforceLookupResponse(
    val shipment: WorkforceShipmentResponse,
    val suggestedActions: List<WorkforceScanAction>,
    val timeline: List<WorkforceTimelineEvent>,
)

data class WorkforceTimelineEvent(
    val status: ShipmentStatus,
    val code: String,
    val description: String,
    val locationName: String?,
    val occurredAt: Instant,
)

data class DeliveryEvidenceResponse(
    /** Pass as `evidenceReference` when recording the delivery attempt. */
    val reference: String,
    val sizeBytes: Int,
)

enum class WorkforceReportKind { DELIVERED, DELIVERY_FAILED, CANCELLED, RETURNING, RETURNED, IN_STOCK }

/** Counts for one worker, or one hub, over [from, to]. */
data class WorkforceReportSummaryResponse(
    val from: LocalDate,
    val to: LocalDate,
    val hubId: UUID?,
    val pickedUp: Long,
    val received: Long,
    val dispatched: Long,
    val outForDelivery: Long,
    val delivered: Long,
    val deliveryFailed: Long,
    val cancelled: Long,
    val returning: Long,
    val returned: Long,
    /** Parcels on the hub's shelves right now (hub reports only). */
    val inStock: Long,
    /** Money collected from recipients in the period (by this courier, or at this hub's deliveries). */
    val collectedAmount: Long,
    val collectedShipments: Long,
)

data class WorkforceReportItem(
    val trackingCode: String,
    val status: ShipmentStatus,
    val recipientName: String,
    val recipientPhone: String,
    val recipientAddress: String,
    val codAmount: Long,
    val amountToCollect: Long,
    val reason: String?,
    val occurredAt: Instant,
)
