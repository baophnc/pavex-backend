package com.pavex.shipment.service

import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.DeliveryAttemptRecordedResponse
import com.pavex.shipment.dto.DeliveryAttemptResponse
import com.pavex.shipment.dto.RecordDeliveryAttemptRequest
import com.pavex.shipment.entity.DeliveryAttempt
import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.ParcelCustodyType
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentForbiddenException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.DeliveryAttemptRepository
import com.pavex.shipment.repository.ParcelRepository
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

@Service
class RecordDeliveryAttemptService(
    private val reader: DeliveryAttemptReader,
    private val writer: DeliveryAttemptWriter,
    private val operations: OperationsShipmentClient,
    private val objectMapper: ObjectMapper,
) {
    fun record(actorUserId: UUID, request: RecordDeliveryAttemptRequest): DeliveryAttemptRecordResult {
        requireActor(actorUserId)
        if (request.assignmentId == ZERO_UUID) {
            throw InvalidShipmentRequestException("invalid_delivery_assignment", "An operational last-mile assignment is required.")
        }
        val parcelCode = request.parcelCode.trim().uppercase()
        val key = request.idempotencyKey.trim()
        val target = reader.read(parcelCode, key)
        val now = Instant.now()
        val occurredAt = request.occurredAt ?: target.existing?.occurredAt ?: now
        validateOccurredAt(now, occurredAt)
        val fingerprint = fingerprint(request, occurredAt)

        target.existing?.let { existing ->
            if (existing.actorUserId != actorUserId) {
                throw ShipmentForbiddenException("delivery_attempt_not_owned", "This delivery attempt belongs to another courier.")
            }
            if (existing.requestFingerprint != fingerprint) {
                throw ShipmentConflictException("idempotency_conflict", "The idempotency key was already used with another payload.")
            }
            if (existing.allParcelsDelivered) {
                completeLastMile(target.shipmentId, request.assignmentId, actorUserId, target.destinationHubId)
            }
            return DeliveryAttemptRecordResult(existing.response, replayed = true)
        }

        validateTarget(actorUserId, target)
        operations.authorizeAssignment(
            shipmentId = target.shipmentId,
            assignmentId = request.assignmentId,
            actorUserId = actorUserId,
            expectedType = OperationsAssignmentType.LAST_MILE_DELIVERY,
            hubId = target.destinationHubId,
        )

        val result = writer.record(actorUserId, request, occurredAt, fingerprint)
        if (result.allParcelsDelivered) {
            completeLastMile(target.shipmentId, request.assignmentId, actorUserId, target.destinationHubId)
        }
        return DeliveryAttemptRecordResult(result.response, replayed = result.replayed)
    }

    private fun completeLastMile(shipmentId: UUID, assignmentId: UUID, actorUserId: UUID, destinationHubId: UUID) {
        operations.completeAssignment(
            shipmentId = shipmentId,
            assignmentId = assignmentId,
            actorUserId = actorUserId,
            expectedType = OperationsAssignmentType.LAST_MILE_DELIVERY,
            hubId = destinationHubId,
            note = "All parcels were delivered with proof.",
        )
    }

    private fun validateTarget(actorUserId: UUID, target: DeliveryAttemptTarget) {
        if (target.lifecycleStatus != ShipmentLifecycleStatus.ACTIVE) {
            throw ShipmentConflictException("shipment_lifecycle_closed", "Shipment lifecycle is already closed.")
        }
        if (target.parcelStatus != ParcelStatus.OUT_FOR_DELIVERY) {
            throw ShipmentConflictException("parcel_not_out_for_delivery", "Parcel must be out for delivery before recording an attempt.")
        }
        if (target.custodyType != ParcelCustodyType.COURIER || target.custodyReferenceId != actorUserId) {
            throw ShipmentForbiddenException("delivery_custody_mismatch", "Only the courier currently holding this parcel may record a delivery attempt.")
        }
    }

    private fun validateOccurredAt(now: Instant, occurredAt: Instant) {
        if (occurredAt.isAfter(now.plus(Duration.ofMinutes(5))) || occurredAt.isBefore(now.minus(Duration.ofDays(7)))) {
            throw InvalidShipmentRequestException(
                "invalid_attempt_time",
                "Attempt time must be within the last 7 days and no more than 5 minutes in the future.",
            )
        }
    }

    private fun fingerprint(request: RecordDeliveryAttemptRequest, occurredAt: Instant): String {
        val fields = linkedMapOf<String, Any?>(
            "assignmentId" to request.assignmentId,
            "parcelCode" to request.parcelCode.trim().uppercase(),
            "outcome" to request.outcome,
            "failureReason" to request.failureReason,
            "proofMethod" to request.proofMethod,
            "recipientName" to request.recipientName?.trim(),
            "evidenceReference" to request.evidenceReference?.trim(),
            "note" to request.note?.trim(),
            "latitude" to request.latitude,
            "longitude" to request.longitude,
            "occurredAt" to occurredAt,
        )
        // Added after launch: omitted when absent so replays of earlier requests keep their fingerprint.
        request.collectedAmount?.let { fields["collectedAmount"] = it }
        val payload = objectMapper.writeValueAsString(fields)
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()))
    }

    private fun requireActor(actorUserId: UUID) {
        if (actorUserId == ZERO_UUID) {
            throw InvalidShipmentRequestException("invalid_delivery_attempt", "Actor is required.")
        }
    }

    companion object {
        private val ZERO_UUID = UUID(0, 0)
    }
}

@Service
class DeliveryAttemptReader(
    private val parcels: ParcelRepository,
    private val attempts: DeliveryAttemptRepository,
) {
    @Transactional(readOnly = true)
    fun read(parcelCode: String, key: String): DeliveryAttemptTarget {
        val parcel = parcels.findByCodeIgnoreCase(parcelCode)
            ?: throw ShipmentNotFoundException("parcel_not_found", "Parcel was not found.")
        val shipment = parcel.shipment
        shipment.parcels.size
        shipment.events.size
        val existing = attempts.findByParcelIdAndIdempotencyKey(parcel.id, key)?.let { attempt ->
            ExistingDeliveryAttempt(
                actorUserId = attempt.actorUserId,
                requestFingerprint = attempt.requestFingerprint,
                occurredAt = attempt.occurredAt,
                response = DeliveryAttemptMapper.response(attempt, shipment),
                allParcelsDelivered = shipment.parcels.all { it.status == ParcelStatus.DELIVERED },
            )
        }
        return DeliveryAttemptTarget(
            shipmentId = shipment.id,
            destinationHubId = shipment.destinationHubId,
            lifecycleStatus = shipment.lifecycleStatus,
            parcelStatus = parcel.status,
            custodyType = parcel.custodyType,
            custodyReferenceId = parcel.custodyReferenceId,
            existing = existing,
        )
    }
}

@Service
class DeliveryAttemptWriter(
    private val parcels: ParcelRepository,
    private val attempts: DeliveryAttemptRepository,
    private val shipments: ShipmentRepository,
    private val outbox: ShipmentOutboxService,
    private val financialEvents: ShipmentFinancialEvents,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun record(
        actorUserId: UUID,
        request: RecordDeliveryAttemptRequest,
        occurredAt: Instant,
        fingerprint: String,
    ): DeliveryAttemptWriteResult {
        val parcelCode = request.parcelCode.trim().uppercase()
        val key = request.idempotencyKey.trim()
        val parcel = parcels.findByCodeIgnoreCase(parcelCode)
            ?: throw ShipmentNotFoundException("parcel_not_found", "Parcel was not found.")
        val shipment = parcel.shipment
        shipment.parcels.size
        shipment.events.size

        attempts.findByParcelIdAndIdempotencyKey(parcel.id, key)?.let { existing ->
            if (existing.actorUserId != actorUserId || existing.requestFingerprint != fingerprint) {
                throw ShipmentConflictException("idempotency_conflict", "The idempotency key was already used with another payload.")
            }
            return DeliveryAttemptWriteResult(
                DeliveryAttemptMapper.response(existing, shipment),
                replayed = true,
                allParcelsDelivered = shipment.parcels.all { it.status == ParcelStatus.DELIVERED },
            )
        }

        if (shipment.lifecycleStatus != ShipmentLifecycleStatus.ACTIVE) {
            throw ShipmentConflictException("shipment_lifecycle_closed", "Shipment lifecycle is already closed.")
        }
        if (parcel.status != ParcelStatus.OUT_FOR_DELIVERY) {
            throw ShipmentConflictException("parcel_not_out_for_delivery", "Parcel must be out for delivery before recording an attempt.")
        }
        if (parcel.custodyType != ParcelCustodyType.COURIER || parcel.custodyReferenceId != actorUserId) {
            throw ShipmentForbiddenException("delivery_custody_mismatch", "Only the courier currently holding this parcel may record a delivery attempt.")
        }

        val recordedAt = Instant.now().let { if (it.isBefore(occurredAt)) occurredAt else it }
        val attempt = try {
            DeliveryAttempt.create(
                shipment = shipment,
                parcel = parcel,
                actorUserId = actorUserId,
                attemptNumber = attempts.countByParcelId(parcel.id).toInt() + 1,
                idempotencyKey = key,
                requestFingerprint = fingerprint,
                outcome = request.outcome,
                failureReason = request.failureReason,
                proofMethod = request.proofMethod,
                recipientName = request.recipientName,
                evidenceReference = request.evidenceReference,
                note = request.note,
                latitude = request.latitude,
                longitude = request.longitude,
                occurredAt = occurredAt,
                createdAt = recordedAt,
            )
        } catch (exception: IllegalArgumentException) {
            throw InvalidShipmentRequestException("invalid_delivery_attempt", exception.message ?: "Delivery attempt is invalid.")
        }

        val delivered = attempt.outcome == DeliveryAttemptOutcome.DELIVERED
        val collectedAmount = DeliveryCollectionPolicy.resolve(shipment, attempt.outcome, request.collectedAmount)
        val lifecycleBefore = shipment.lifecycleStatus
        try {
            // Collected before the parcel transition: delivering the last parcel closes the shipment.
            if (collectedAmount != null) {
                shipment.recordCollection(
                    amount = collectedAmount,
                    courierUserId = actorUserId,
                    attemptId = attempt.id,
                    parcelId = parcel.id,
                    hubId = shipment.destinationHubId,
                    locationName = shipment.recipient.wardName,
                    occurredAt = occurredAt,
                    recordedAt = recordedAt,
                )
            }
            shipment.transitionParcel(
                parcelId = parcel.id,
                nextStatus = if (delivered) ParcelStatus.DELIVERED else ParcelStatus.DELIVERY_FAILED,
                eventCode = if (delivered) "parcel.delivered" else "parcel.delivery-failed",
                description = if (delivered) "Đã giao hàng và ghi nhận bằng chứng giao nhận."
                    else "Giao hàng không thành công: ${ShipmentVietnameseText.deliveryFailure(attempt.failureReason)}.",
                actorUserId = actorUserId,
                hubId = shipment.destinationHubId,
                locationName = shipment.recipient.wardName,
                occurredAt = occurredAt,
                recordedAt = recordedAt,
                assignmentId = request.assignmentId,
            )
            attempts.save(attempt)
            shipments.saveAndFlush(shipment)
        } catch (exception: IllegalStateException) {
            throw ShipmentConflictException("invalid_delivery_transition", exception.message ?: "Delivery transition is invalid.")
        } catch (exception: DataIntegrityViolationException) {
            throw ShipmentConflictException("delivery_attempt_changed", "The delivery attempt was recorded concurrently.")
        }

        outbox.enqueue(
            aggregateId = shipment.id,
            aggregateCode = shipment.trackingCode,
            eventType = "shipment.delivery-attempt-recorded",
            payloadJson = objectMapper.writeValueAsString(
                mapOf(
                    "shipmentId" to shipment.id,
                    "trackingCode" to shipment.trackingCode,
                    "parcelId" to parcel.id,
                    "parcelCode" to parcel.code,
                    "assignmentId" to request.assignmentId,
                    "attemptNumber" to attempt.attemptNumber,
                    "outcome" to attempt.outcome,
                    "failureReason" to attempt.failureReason,
                    "proofMethod" to attempt.proofMethod,
                    "collectedAmount" to collectedAmount,
                    "actorUserId" to actorUserId,
                    "occurredAt" to occurredAt,
                ),
            ),
            occurredAt = occurredAt,
        )
        if (collectedAmount != null) financialEvents.cashCollected(shipment)
        financialEvents.closedIfChanged(shipment, lifecycleBefore)
        return DeliveryAttemptWriteResult(
            DeliveryAttemptMapper.response(attempt, shipment),
            replayed = false,
            allParcelsDelivered = shipment.parcels.all { it.status == ParcelStatus.DELIVERED },
        )
    }
}

private object DeliveryAttemptMapper {
    fun response(attempt: DeliveryAttempt, shipment: com.pavex.shipment.entity.Shipment) =
        DeliveryAttemptRecordedResponse(
            attempt = DeliveryAttemptResponse(
                parcelCode = attempt.parcel.code,
                attemptNumber = attempt.attemptNumber,
                outcome = attempt.outcome,
                failureReason = attempt.failureReason,
                proofMethod = attempt.proofMethod,
                recipientName = attempt.recipientName,
                evidenceReference = attempt.evidenceReference,
                note = attempt.note,
                latitude = attempt.latitude,
                longitude = attempt.longitude,
                occurredAt = attempt.occurredAt,
                collectedAmount = shipment.collectedAmount.takeIf { shipment.collectionAttemptId == attempt.id },
            ),
            shipment = ShipmentResponseMapper.detail(shipment),
        )
}

data class DeliveryAttemptTarget(
    val shipmentId: UUID,
    val destinationHubId: UUID,
    val lifecycleStatus: ShipmentLifecycleStatus,
    val parcelStatus: ParcelStatus,
    val custodyType: ParcelCustodyType,
    val custodyReferenceId: UUID?,
    val existing: ExistingDeliveryAttempt?,
)

data class ExistingDeliveryAttempt(
    val actorUserId: UUID,
    val requestFingerprint: String,
    val occurredAt: Instant,
    val response: DeliveryAttemptRecordedResponse,
    val allParcelsDelivered: Boolean,
)

data class DeliveryAttemptWriteResult(
    val response: DeliveryAttemptRecordedResponse,
    val replayed: Boolean,
    val allParcelsDelivered: Boolean,
)

data class DeliveryAttemptRecordResult(
    val response: DeliveryAttemptRecordedResponse,
    val replayed: Boolean,
)
