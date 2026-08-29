package com.pavex.shipment.service

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
    private val parcels: ParcelRepository,
    private val attempts: DeliveryAttemptRepository,
    private val shipments: ShipmentRepository,
    private val outbox: ShipmentOutboxService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun record(actorUserId: UUID, request: RecordDeliveryAttemptRequest): DeliveryAttemptRecordResult {
        requireActor(actorUserId)
        val parcelCode = request.parcelCode.trim().uppercase()
        val key = request.idempotencyKey.trim()
        val parcel = parcels.findByCodeIgnoreCase(parcelCode)
            ?: throw ShipmentNotFoundException("parcel_not_found", "Parcel was not found.")
        val shipment = parcel.shipment
        shipment.parcels.size
        shipment.events.size

        val existing = attempts.findByParcelIdAndIdempotencyKey(parcel.id, key)
        if (existing != null) {
            if (existing.actorUserId != actorUserId) {
                throw ShipmentForbiddenException("delivery_attempt_not_owned", "This delivery attempt belongs to another courier.")
            }
            val replayOccurredAt = request.occurredAt ?: existing.occurredAt
            if (existing.requestFingerprint != fingerprint(request, replayOccurredAt)) {
                throw ShipmentConflictException("idempotency_conflict", "The idempotency key was already used with another payload.")
            }
            return DeliveryAttemptRecordResult(response(existing, shipment), replayed = true)
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

        val now = Instant.now()
        val occurredAt = request.occurredAt ?: now
        if (occurredAt.isAfter(now.plus(Duration.ofMinutes(5))) || occurredAt.isBefore(now.minus(Duration.ofDays(7)))) {
            throw InvalidShipmentRequestException(
                "invalid_attempt_time",
                "Attempt time must be within the last 7 days and no more than 5 minutes in the future.",
            )
        }
        val recordedAt = if (now.isBefore(occurredAt)) occurredAt else now
        val attempt = try {
            DeliveryAttempt.create(
                shipment = shipment,
                parcel = parcel,
                actorUserId = actorUserId,
                attemptNumber = attempts.countByParcelId(parcel.id).toInt() + 1,
                idempotencyKey = key,
                requestFingerprint = fingerprint(request, occurredAt),
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
        try {
            shipment.transitionParcel(
                parcelId = parcel.id,
                nextStatus = if (delivered) ParcelStatus.DELIVERED else ParcelStatus.DELIVERY_FAILED,
                eventCode = if (delivered) "parcel.delivered" else "parcel.delivery-failed",
                description = if (delivered) "Parcel delivered with proof." else "Delivery attempt failed: ${attempt.failureReason}.",
                actorUserId = actorUserId,
                hubId = shipment.destinationHubId,
                locationName = shipment.recipient.wardName,
                occurredAt = occurredAt,
                recordedAt = recordedAt,
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
                    "attemptNumber" to attempt.attemptNumber,
                    "outcome" to attempt.outcome,
                    "failureReason" to attempt.failureReason,
                    "proofMethod" to attempt.proofMethod,
                    "actorUserId" to actorUserId,
                    "occurredAt" to occurredAt,
                ),
            ),
            occurredAt = occurredAt,
        )
        return DeliveryAttemptRecordResult(response(attempt, shipment), replayed = false)
    }

    private fun response(attempt: DeliveryAttempt, shipment: com.pavex.shipment.entity.Shipment) =
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
            ),
            shipment = ShipmentResponseMapper.detail(shipment),
        )

    private fun fingerprint(request: RecordDeliveryAttemptRequest, occurredAt: Instant): String {
        val payload = objectMapper.writeValueAsString(
            linkedMapOf(
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
            ),
        )
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()))
    }

    private fun requireActor(actorUserId: UUID) {
        if (actorUserId == UUID(0, 0)) {
            throw InvalidShipmentRequestException("invalid_delivery_attempt", "Actor is required.")
        }
    }
}

data class DeliveryAttemptRecordResult(
    val response: DeliveryAttemptRecordedResponse,
    val replayed: Boolean,
)
