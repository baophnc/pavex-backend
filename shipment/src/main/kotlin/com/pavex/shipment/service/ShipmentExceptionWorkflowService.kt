package com.pavex.shipment.service

import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.ForwardRecipientRequest
import com.pavex.shipment.dto.ForwardRecipientResponse
import com.pavex.shipment.dto.ShipmentExceptionRequestResponse
import com.pavex.shipment.dto.ShipmentExceptionStateResponse
import com.pavex.shipment.dto.ShipmentWeightAdjustmentResponse
import com.pavex.shipment.entity.ForwardRecipientSnapshot
import com.pavex.shipment.entity.ParcelStage
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentExceptionRequest
import com.pavex.shipment.entity.ShipmentExceptionRequestKind
import com.pavex.shipment.entity.ShipmentExceptionRequestStatus
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentRoutingStatus
import com.pavex.shipment.entity.ShipmentWeightAdjustment
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.ShipmentExceptionRequestRepository
import com.pavex.shipment.repository.ShipmentRepository
import com.pavex.shipment.repository.ShipmentWeightAdjustmentRepository
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ShipmentExceptionWorkflowService(
    private val shipments: ShipmentRepository,
    private val requests: ShipmentExceptionRequestRepository,
    private val adjustments: ShipmentWeightAdjustmentRepository,
    private val operations: OperationsShipmentClient,
    private val network: NetworkShipmentClient,
    private val outbox: ShipmentOutboxService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun get(shipmentId: UUID): ShipmentExceptionStateResponse = state(load(shipmentId))

    @Transactional
    fun placeHold(shipmentId: UUID, actor: UUID, reason: String, hubId: UUID?): ShipmentExceptionStateResponse {
        val shipment = load(shipmentId)
        val now = Instant.now()
        try {
            shipment.placeHold(reason, actor, hubId, now)
            shipments.saveAndFlush(shipment)
            publish(shipment, "shipment.hold.placed", actor, now, mapOf("reason" to reason.trim(), "hubId" to hubId))
            return state(shipment)
        } catch (exception: IllegalStateException) {
            throw conflict("shipment_hold_invalid", exception)
        }
    }

    @Transactional
    fun releaseHold(shipmentId: UUID, actor: UUID, reason: String, hubId: UUID?): ShipmentExceptionStateResponse {
        val shipment = load(shipmentId)
        val now = Instant.now()
        try {
            shipment.releaseHold(reason, actor, hubId, now)
            shipments.saveAndFlush(shipment)
            publish(shipment, "shipment.hold.released", actor, now, mapOf("reason" to reason.trim(), "hubId" to hubId))
            return state(shipment)
        } catch (exception: IllegalStateException) {
            throw conflict("shipment_hold_release_invalid", exception)
        }
    }

    @Transactional
    fun requestReturn(shipmentId: UUID, actor: UUID, reason: String, hubId: UUID? = null) =
        request(shipmentId, actor, ShipmentExceptionRequestKind.RETURN, reason, hubId, null)
    @Transactional
    fun requestCancellation(shipmentId: UUID, actor: UUID, reason: String, hubId: UUID? = null) =
        request(shipmentId, actor, ShipmentExceptionRequestKind.CANCELLATION, reason, hubId, null)

    @Transactional
    fun requestForward(shipmentId: UUID, actor: UUID, reason: String, recipient: ForwardRecipientRequest, hubId: UUID? = null) = request(
        shipmentId,
        actor,
        ShipmentExceptionRequestKind.FORWARD,
        reason,
        hubId,
        ForwardRecipientSnapshot(
            requireText(recipient.contactName, 120, "contactName"),
            requireText(recipient.contactPhone, 20, "contactPhone"),
            requireText(recipient.addressLine, 500, "addressLine"),
            requireText(recipient.provinceCode, 20, "provinceCode"),
            requireText(recipient.wardCode, 20, "wardCode"),
        ),
    )

    @Transactional
    fun review(shipmentId: UUID, requestId: UUID, actor: UUID, approve: Boolean, note: String?): ShipmentExceptionStateResponse {
        val shipment = load(shipmentId)
        val request = requests.findByIdAndShipmentId(requestId, shipmentId)
            ?: throw ShipmentNotFoundException("exception_request_not_found", "Shipment exception request was not found.")
        if (request.requestedByUserId == actor) {
            throw ShipmentConflictException(
                "exception_self_approval_not_allowed",
                "The requester cannot approve or reject their own shipment exception request; a second authorized approver is required.",
            )
        }
        val now = Instant.now()

        try {
            request.review(approve, actor, note, now)
            val prefix = prefix(request.kind)
            if (!approve) {
                shipment.recordAdministrativeEvent(
                    "$prefix.rejected",
                    note?.trim()?.takeIf { it.isNotEmpty() } ?: "${request.kind.name.lowercase()} request rejected.",
                    actor,
                    null,
                    now,
                    payload(request),
                )
            } else {
                when (request.kind) {
                    ShipmentExceptionRequestKind.RETURN -> {
                        cancelForwardExecution(shipment, actor, "Shipment return approved.")
                        shipment.prepareReturn(request.reason, actor, now, payload(request))
                        request.markProcessing(now)
                        shipment.recordAdministrativeEvent(
                            "$prefix.processing",
                            "Return approved. Parcels are ready for reverse movement scans.",
                            actor,
                            null,
                            now.plusNanos(1),
                            payload(request),
                        )
                    }
                    ShipmentExceptionRequestKind.FORWARD -> {
                        cancelForwardExecution(shipment, actor, "Shipment forwarding approved.")
                        request.markProcessing(now)
                        shipment.recordAdministrativeEvent(
                            "$prefix.approved",
                            "Forward request approved; replacement routing is required before movement resumes.",
                            actor,
                            null,
                            now,
                            payload(request),
                        )
                        if (!shipment.isOnHold) shipment.placeHold("Forwarding approved; waiting for replacement routing.", actor, null, now.plusNanos(1))
                    }
                    ShipmentExceptionRequestKind.CANCELLATION -> {
                        operations.cancel(shipment.id, "Shipment cancellation approved.")
                        releaseNetwork(shipment, actor, "Shipment cancellation approved.")
                        shipment.recordAdministrativeEvent("$prefix.approved", "Cancellation request approved.", actor, null, now, payload(request))
                        if (shipment.parcels.all { it.stage == ParcelStage.AWAITING_PICKUP }) {
                            shipment.cancel(request.reason, actor, now.plusNanos(1))
                            request.complete(actor, now.plusNanos(2))
                            shipment.recordAdministrativeEvent("$prefix.completed", request.reason, actor, null, now.plusNanos(2), payload(request))
                        } else {
                            request.markProcessing(now)
                            if (!shipment.isOnHold) shipment.placeHold("Cancellation approved; parcel recovery is required.", actor, null, now.plusNanos(1))
                            shipment.recordAdministrativeEvent(
                                "$prefix.processing",
                                "Cancellation approved and waiting for parcel recovery at a hub.",
                                actor,
                                null,
                                now.plusNanos(2),
                                payload(request),
                            )
                        }
                    }
                }
            }
            requests.save(request)
            shipments.saveAndFlush(shipment)
            publish(shipment, "$prefix.${request.status.name.lowercase().replace('_', '-')}", actor, now, requestResponse(request))
            return state(shipment)
        } catch (exception: IllegalStateException) {
            throw conflict("exception_review_invalid", exception)
        } catch (exception: OptimisticLockingFailureException) {
            throw ShipmentConflictException("shipment_changed", "Shipment changed while the exception request was being reviewed.")
        }
    }

    @Transactional
    fun completeCancellation(shipmentId: UUID, requestId: UUID, actor: UUID, note: String?): ShipmentExceptionStateResponse {
        val shipment = load(shipmentId)
        val request = requests.findByIdAndShipmentId(requestId, shipmentId)
            ?.takeIf { it.kind == ShipmentExceptionRequestKind.CANCELLATION }
            ?: throw ShipmentNotFoundException("cancellation_request_not_found", "Cancellation request was not found.")
        val now = Instant.now()
        try {
            check(request.status == ShipmentExceptionRequestStatus.APPROVED || request.status == ShipmentExceptionRequestStatus.PROCESSING) {
                "Cancellation must be approved and processing before it can be completed."
            }
            operations.cancel(shipment.id, "Cancellation completed after parcel recovery.")
            releaseNetwork(shipment, actor, "Cancellation completed after parcel recovery.")
            shipment.cancelRecovered(request.reason, actor, now, payload(request, note))
            request.complete(actor, now)
            requests.save(request)
            shipments.saveAndFlush(shipment)
            publish(shipment, "shipment.cancellation.completed", actor, now, requestResponse(request))
            return state(shipment)
        } catch (exception: IllegalStateException) {
            throw conflict("cancellation_recovery_incomplete", exception)
        }
    }

    @Transactional
    fun adjustWeight(
        shipmentId: UUID,
        parcelId: UUID,
        actor: UUID,
        actualWeightKg: BigDecimal,
        reason: String,
        hubId: UUID?,
    ): ShipmentExceptionStateResponse {
        val shipment = load(shipmentId)
        val parcel = shipment.parcels.singleOrNull { it.id == parcelId }
            ?: throw InvalidShipmentRequestException("parcel_not_found", "Parcel does not belong to this shipment.")
        if (parcel.stage == ParcelStage.AT_HUB && parcel.currentHubId != hubId) {
            throw ShipmentConflictException("weight_hub_mismatch", "Weight adjustment hub must match the parcel current hub.")
        }
        if (adjustments.existsByShipmentIdAndReviewCompletedFalse(shipmentId)) {
            throw ShipmentConflictException("weight_review_pending", "Complete the existing weight review before recording another adjustment.")
        }
        val now = Instant.now()
        val oldActual = parcel.actualWeightKg
        val oldChargeable = parcel.chargeableWeightKg
        val normalizedReason = requireText(reason, 1000, "reason")
        try {
            shipment.adjustParcelWeight(parcelId, actualWeightKg, now)
            val adjustment = adjustments.save(
                ShipmentWeightAdjustment(
                    shipmentId = shipment.id,
                    parcelId = parcel.id,
                    oldActualWeightKg = oldActual,
                    newActualWeightKg = parcel.actualWeightKg,
                    oldChargeableWeightKg = oldChargeable,
                    newChargeableWeightKg = parcel.chargeableWeightKg,
                    reason = normalizedReason,
                    actorUserId = actor,
                    hubId = hubId,
                    adjustedAt = now,
                ),
            )
            shipment.recordAdministrativeEvent("shipment.weight-adjusted", normalizedReason, actor, hubId, now, payload(adjustment))
            if (!shipment.isOnHold) shipment.placeHold("Weight changed; pricing and routing review is required.", actor, hubId, now.plusNanos(1))
            shipments.saveAndFlush(shipment)
            publish(shipment, "shipment.weight-adjusted", actor, now, adjustmentResponse(adjustment))
            return state(shipment)
        } catch (exception: IllegalStateException) {
            throw conflict("weight_adjustment_invalid", exception)
        }
    }

    @Transactional
    fun completeWeightReview(shipmentId: UUID, adjustmentId: UUID, actor: UUID, note: String): ShipmentExceptionStateResponse {
        val shipment = load(shipmentId)
        val adjustment = adjustments.findByIdAndShipmentId(adjustmentId, shipmentId)
            ?: throw ShipmentNotFoundException("weight_adjustment_not_found", "Weight adjustment was not found.")
        if (adjustment.actorUserId == actor) {
            throw ShipmentConflictException(
                "weight_self_review_not_allowed",
                "The user who recorded the weight adjustment cannot complete its review; a second authorized approver is required.",
            )
        }
        val totalWeight = shipment.parcels.fold(BigDecimal.ZERO) { total, parcel -> total + parcel.chargeableWeightKg }
        if (shipment.routeRevision > 0 && shipment.reservedWeightKg > BigDecimal.ZERO && totalWeight > shipment.reservedWeightKg) {
            throw ShipmentConflictException(
                "weight_exceeds_reserved_capacity",
                "Adjusted shipment weight exceeds reserved network capacity; replacement routing is required before review can complete.",
            )
        }
        val now = Instant.now()
        try {
            adjustment.completeReview(actor, note, now)
            shipment.completeWeightReview(now)
            shipment.recordAdministrativeEvent("shipment.weight-review.completed", note, actor, adjustment.hubId, now, payload(adjustment))
            adjustments.save(adjustment)
            shipments.saveAndFlush(shipment)
            publish(shipment, "shipment.weight-review.completed", actor, now, adjustmentResponse(adjustment))
            return state(shipment)
        } catch (exception: IllegalStateException) {
            throw conflict("weight_review_invalid", exception)
        }
    }

    @Transactional
    fun completeRoutingRequest(shipmentId: UUID, requestId: UUID, actor: UUID, note: String?): ShipmentExceptionStateResponse {
        val shipment = load(shipmentId)
        val request = requests.findByIdAndShipmentId(requestId, shipmentId)
            ?: throw ShipmentNotFoundException("exception_request_not_found", "Shipment exception request was not found.")
        check(request.kind == ShipmentExceptionRequestKind.FORWARD || request.kind == ShipmentExceptionRequestKind.RETURN) {
            "Only return or forward requests use routing completion."
        }
        val now = Instant.now()
        try {
            request.complete(actor, now)
            shipment.recordAdministrativeEvent("${prefix(request.kind)}.completed", note?.trim()?.takeIf { it.isNotEmpty() } ?: "Exception workflow completed.", actor, null, now, payload(request, note))
            requests.save(request)
            shipments.saveAndFlush(shipment)
            publish(shipment, "${prefix(request.kind)}.completed", actor, now, requestResponse(request))
            return state(shipment)
        } catch (exception: IllegalStateException) {
            throw conflict("exception_completion_invalid", exception)
        }
    }

    private fun request(
        shipmentId: UUID,
        actor: UUID,
        kind: ShipmentExceptionRequestKind,
        reason: String,
        hubId: UUID?,
        recipient: ForwardRecipientSnapshot?,
    ): ShipmentExceptionStateResponse {
        val shipment = load(shipmentId)
        if (shipment.lifecycleStatus != ShipmentLifecycleStatus.ACTIVE) {
            throw ShipmentConflictException("shipment_lifecycle_closed", "Only active shipments can receive exception requests.")
        }
        val active = listOf(
            ShipmentExceptionRequestStatus.PENDING_APPROVAL,
            ShipmentExceptionRequestStatus.APPROVED,
            ShipmentExceptionRequestStatus.PROCESSING,
        )
        if (requests.existsByShipmentIdAndStatusIn(shipmentId, active)) {
            throw ShipmentConflictException("conflicting_exception_request", "Another return, forward, or cancellation workflow is already active for this shipment.")
        }
        val now = Instant.now()
        val request = ShipmentExceptionRequest.create(shipmentId, kind, reason, actor, now, hubId, recipient)
        requests.save(request)
        shipment.recordAdministrativeEvent("${prefix(kind)}.requested", request.reason, actor, hubId, now, payload(request))
        shipments.saveAndFlush(shipment)
        publish(shipment, "${prefix(kind)}.requested", actor, now, requestResponse(request))
        return state(shipment)
    }

    private fun cancelForwardExecution(shipment: Shipment, actor: UUID, reason: String) {
        operations.cancel(shipment.id, reason)
        releaseNetwork(shipment, actor, reason)
    }

    private fun releaseNetwork(shipment: Shipment, actor: UUID, reason: String) {
        if (shipment.routeRevision <= 0 || shipment.routingStatus == ShipmentRoutingStatus.RELEASED) return
        if (shipment.capacityReservationBatchId != null) {
            network.release(shipment.id, shipment.routeRevision, actor, reason)
        }
        shipment.markRouteReleased(Instant.now())
    }

    private fun load(shipmentId: UUID): Shipment = shipments.findById(shipmentId).orElseThrow {
        ShipmentNotFoundException("shipment_not_found", "Shipment was not found.")
    }

    private fun state(shipment: Shipment): ShipmentExceptionStateResponse {
        val requestRows = requests.findAllByShipmentIdOrderByRequestedAtAsc(shipment.id)
        val adjustmentRows = adjustments.findAllByShipmentIdOrderByAdjustedAtAsc(shipment.id)
        return ShipmentExceptionStateResponse(
            shipmentId = shipment.id,
            trackingCode = shipment.trackingCode,
            isOnHold = shipment.isOnHold,
            holdReason = shipment.holdReason,
            holdActorUserId = shipment.holdActorUserId,
            holdHubId = shipment.holdHubId,
            heldAt = shipment.heldAt,
            hasPendingWeightReview = shipment.hasPendingWeightReview,
            returnRequest = requestRows.lastOrNull { it.kind == ShipmentExceptionRequestKind.RETURN }?.let(::requestResponse),
            forwardRequest = requestRows.lastOrNull { it.kind == ShipmentExceptionRequestKind.FORWARD }?.let(::requestResponse),
            cancellationRequest = requestRows.lastOrNull { it.kind == ShipmentExceptionRequestKind.CANCELLATION }?.let(::requestResponse),
            weightAdjustments = adjustmentRows.map(::adjustmentResponse),
        )
    }

    private fun requestResponse(request: ShipmentExceptionRequest) = ShipmentExceptionRequestResponse(
        requestId = request.id,
        kind = request.kind,
        status = request.status,
        reason = request.reason,
        hubId = request.hubId,
        requestedByUserId = request.requestedByUserId,
        requestedAt = request.requestedAt,
        reviewedByUserId = request.reviewedByUserId,
        reviewedAt = request.reviewedAt,
        reviewNote = request.reviewNote,
        routingRequired = request.routingRequired,
        forwardRecipient = request.forwardContactName?.let {
            ForwardRecipientResponse(
                it,
                requireNotNull(request.forwardContactPhone),
                requireNotNull(request.forwardAddressLine),
                requireNotNull(request.forwardProvinceCode),
                requireNotNull(request.forwardWardCode),
            )
        },
        completedByUserId = request.completedByUserId,
        completedAt = request.completedAt,
        version = request.version,
    )

    private fun adjustmentResponse(adjustment: ShipmentWeightAdjustment) = ShipmentWeightAdjustmentResponse(
        adjustmentId = adjustment.id,
        parcelId = adjustment.parcelId,
        oldActualWeightKg = adjustment.oldActualWeightKg,
        newActualWeightKg = adjustment.newActualWeightKg,
        oldChargeableWeightKg = adjustment.oldChargeableWeightKg,
        newChargeableWeightKg = adjustment.newChargeableWeightKg,
        reason = adjustment.reason,
        actorUserId = adjustment.actorUserId,
        hubId = adjustment.hubId,
        adjustedAt = adjustment.adjustedAt,
        reviewCompleted = adjustment.reviewCompleted,
        reviewedByUserId = adjustment.reviewedByUserId,
        reviewedAt = adjustment.reviewedAt,
        reviewNote = adjustment.reviewNote,
    )

    private fun publish(shipment: Shipment, eventType: String, actor: UUID, occurredAt: Instant, data: Any) {
        outbox.enqueue(
            aggregateId = shipment.id,
            aggregateCode = shipment.trackingCode,
            eventType = eventType,
            payloadJson = objectMapper.writeValueAsString(mapOf("shipmentId" to shipment.id, "trackingCode" to shipment.trackingCode, "actorUserId" to actor, "data" to data)),
            occurredAt = occurredAt,
        )
    }

    private fun payload(value: Any, note: String? = null): String = objectMapper.writeValueAsString(
        if (note == null) value else mapOf("value" to value, "note" to note),
    )

    private fun prefix(kind: ShipmentExceptionRequestKind): String = when (kind) {
        ShipmentExceptionRequestKind.RETURN -> "shipment.return"
        ShipmentExceptionRequestKind.FORWARD -> "shipment.forward"
        ShipmentExceptionRequestKind.CANCELLATION -> "shipment.cancellation"
    }

    private fun requireText(value: String?, max: Int, field: String): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty() || normalized.length > max) {
            throw InvalidShipmentRequestException("invalid_$field", "$field is required and must not exceed $max characters.")
        }
        return normalized
    }

    private fun conflict(code: String, exception: IllegalStateException) = ShipmentConflictException(code, exception.message ?: "Shipment exception workflow is invalid.")
}
