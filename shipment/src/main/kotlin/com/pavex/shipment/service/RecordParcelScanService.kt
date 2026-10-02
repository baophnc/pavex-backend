package com.pavex.shipment.service

import com.pavex.contracts.network.ShipmentOperationalHubSnapshot
import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.dto.RecordParcelScanRequest
import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentApiException
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.ParcelRepository
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RecordParcelScanService(
    private val reader: ParcelScanTargetReader,
    private val writer: ParcelScanWriter,
    private val network: NetworkShipmentClient,
    private val operationsGuard: ShipmentOperationsScanGuard,
    private val controlSignals: ShipmentControlSignalService,
) {
    fun record(actorUserId: UUID, request: RecordParcelScanRequest): ShipmentDetailResponse {
        if (actorUserId == UUID(0, 0) || request.hubId == UUID(0, 0)) {
            throw InvalidShipmentRequestException("invalid_scan", "Parcel code, hub and actor are required.")
        }

        val parcelCode = request.parcelCode.trim().uppercase()
        if (parcelCode.isEmpty() || parcelCode.length > 40) {
            throw InvalidShipmentRequestException("invalid_scan", "Parcel code, hub and actor are required.")
        }

        val now = Instant.now()
        val occurredAt = request.occurredAt ?: now
        if (occurredAt.isAfter(now.plus(Duration.ofMinutes(5))) || occurredAt.isBefore(now.minus(Duration.ofDays(30)))) {
            throw InvalidShipmentRequestException(
                "invalid_scan_time",
                "Scan time must be within the last 30 days and no more than 5 minutes in the future.",
            )
        }

        val target = reader.read(parcelCode)
        try {
        if (target.lifecycleStatus != ShipmentLifecycleStatus.ACTIVE) {
            throw ShipmentConflictException("shipment_lifecycle_closed", "Shipment lifecycle is already closed.")
        }
        if (target.routeRevision <= 0) {
            throw ShipmentConflictException("shipment_route_missing", "Shipment does not have an active network route.")
        }

        val hub = network.resolveOperationalHub(target.shipmentId, target.routeRevision, request.hubId)
        if (!hub.active) {
            throw InvalidShipmentRequestException("hub_not_active", "The scan hub does not exist or is inactive.")
        }
        if (!ParcelScanPolicy.isHubAllowed(target.originHubId, target.destinationHubId, hub, request.nextStatus)) {
            throw InvalidShipmentRequestException(
                "scan_hub_mismatch",
                "This hub is not valid for the requested parcel status.",
            )
        }
        val operationsContext = operationsGuard.validate(actorUserId, target, hub, request)

        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultDescription(request.nextStatus, hub.hubName)
        if (description.length > 500) {
            throw InvalidShipmentRequestException(
                "invalid_scan_description",
                "Scan description must not exceed 500 characters.",
            )
        }

        val receivedAt = Instant.now().let { received ->
            if (received.isBefore(occurredAt)) occurredAt else received
        }
        val response = writer.apply(
            actorUserId = actorUserId,
            parcelCode = parcelCode,
            hub = hub,
            nextStatus = request.nextStatus,
            description = description,
            occurredAt = occurredAt,
            receivedAt = receivedAt,
            assignmentId = request.assignmentId,
            routeLegId = request.routeLegId,
        )
        operationsGuard.afterPersist(actorUserId, target, request, operationsContext, response)
        return response
        } catch (exception: ShipmentApiException) {
            runCatching { controlSignals.recordRejectedMovement(actorUserId, target, request, exception) }
            throw exception
        }
    }

    private fun defaultDescription(status: ParcelStatus?, hubName: String): String =
        ShipmentVietnameseText.scan(status, hubName)
}

@Service
class ParcelScanTargetReader(
    private val parcels: ParcelRepository,
) {
    @Transactional(readOnly = true)
    fun read(parcelCode: String): ParcelScanTarget {
        val parcel = parcels.findByCodeIgnoreCase(parcelCode) ?: throw notFound()
        val shipment = parcel.shipment
        return ParcelScanTarget(
            shipmentId = shipment.id,
            parcelId = parcel.id,
            parcelCode = parcel.code,
            parcelStatus = parcel.status,
            currentHubId = parcel.currentHubId,
            routeRevision = shipment.routeRevision,
            originHubId = shipment.originHubId,
            destinationHubId = shipment.destinationHubId,
            lifecycleStatus = shipment.lifecycleStatus,
        )
    }

    private fun notFound() = ShipmentNotFoundException("parcel_not_found", "Parcel was not found.")
}

@Service
class ParcelScanWriter(
    private val parcels: ParcelRepository,
    private val shipments: ShipmentRepository,
    private val outbox: ShipmentOutboxService,
    private val financialEvents: ShipmentFinancialEvents,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun apply(
        actorUserId: UUID,
        parcelCode: String,
        hub: ShipmentOperationalHubSnapshot,
        nextStatus: ParcelStatus?,
        description: String,
        occurredAt: Instant,
        receivedAt: Instant,
        assignmentId: UUID?,
        routeLegId: UUID?,
    ): ShipmentDetailResponse {
        val parcel = parcels.findByCodeIgnoreCase(parcelCode)
            ?: throw ShipmentNotFoundException("parcel_not_found", "Parcel was not found.")
        val shipment = parcel.shipment
        shipment.parcels.size
        shipment.events.size

        if (shipment.lifecycleStatus != ShipmentLifecycleStatus.ACTIVE) {
            throw ShipmentConflictException("shipment_lifecycle_closed", "Shipment lifecycle is already closed.")
        }
        if (!ParcelScanPolicy.isHubAllowed(shipment.originHubId, shipment.destinationHubId, hub, nextStatus)) {
            throw InvalidShipmentRequestException(
                "scan_hub_mismatch",
                "This hub is not valid for the requested parcel status.",
            )
        }

        val previousStatus = parcel.status
        val lifecycleBefore = shipment.lifecycleStatus
        val checkpoint = nextStatus == null || nextStatus == previousStatus
        try {
            if (checkpoint) {
                shipment.recordParcelCheckpoint(
                    parcelId = parcel.id,
                    description = description,
                    actorUserId = actorUserId,
                    hubId = hub.hubId,
                    locationName = hub.hubName,
                    occurredAt = occurredAt,
                    recordedAt = receivedAt,
                    routeLegId = routeLegId,
                    assignmentId = assignmentId,
                )
            } else {
                val targetStatus = requireNotNull(nextStatus)
                shipment.transitionParcel(
                    parcelId = parcel.id,
                    nextStatus = targetStatus,
                    eventCode = eventCode(targetStatus),
                    description = description,
                    actorUserId = actorUserId,
                    hubId = hub.hubId,
                    locationName = hub.hubName,
                    occurredAt = occurredAt,
                    recordedAt = receivedAt,
                    routeLegId = routeLegId,
                    assignmentId = assignmentId,
                )
            }
            shipments.saveAndFlush(shipment)
        } catch (exception: IllegalStateException) {
            throw ShipmentConflictException(
                "invalid_parcel_transition",
                exception.message ?: "Parcel status transition is invalid.",
            )
        } catch (exception: ObjectOptimisticLockingFailureException) {
            throw ShipmentConflictException(
                "shipment_changed",
                "Shipment changed while the scan was being processed.",
            )
        }

        val currentStatus = parcel.status
        outbox.enqueue(
            aggregateId = shipment.id,
            aggregateCode = shipment.trackingCode,
            eventType = "shipment.parcel-scanned",
            payloadJson = objectMapper.writeValueAsString(
                ParcelScannedOutboxPayload(
                    shipmentId = shipment.id,
                    trackingCode = shipment.trackingCode,
                    parcelId = parcel.id,
                    parcelCode = parcel.code,
                    previousStatus = previousStatus,
                    currentStatus = currentStatus,
                    checkpoint = checkpoint,
                    hubId = hub.hubId,
                    hubCode = hub.hubCode,
                    hubName = hub.hubName,
                    routeLegId = parcel.currentRouteLegId,
                    actorUserId = actorUserId,
                    occurredAt = occurredAt,
                    recordedAt = receivedAt,
                ),
            ),
            occurredAt = occurredAt,
        )
        financialEvents.closedIfChanged(shipment, lifecycleBefore)
        return ShipmentResponseMapper.detail(shipment)
    }

    private fun eventCode(status: ParcelStatus): String =
        "parcel.${status.name.lowercase().replace('_', '-')}"
}

data class ParcelScanTarget(
    val shipmentId: UUID,
    val parcelId: UUID,
    val parcelCode: String,
    val parcelStatus: ParcelStatus,
    val currentHubId: UUID?,
    val routeRevision: Int,
    val originHubId: UUID,
    val destinationHubId: UUID,
    val lifecycleStatus: ShipmentLifecycleStatus,
)

private data class ParcelScannedOutboxPayload(
    val shipmentId: UUID,
    val trackingCode: String,
    val parcelId: UUID,
    val parcelCode: String,
    val previousStatus: ParcelStatus,
    val currentStatus: ParcelStatus,
    val checkpoint: Boolean,
    val hubId: UUID,
    val hubCode: String,
    val hubName: String,
    /** The route leg the parcel is on after the scan: set while it rides a line-haul truck. */
    val routeLegId: UUID?,
    val actorUserId: UUID,
    val occurredAt: Instant,
    val recordedAt: Instant,
)
