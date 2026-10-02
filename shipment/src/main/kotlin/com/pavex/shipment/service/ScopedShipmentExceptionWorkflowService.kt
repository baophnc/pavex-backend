package com.pavex.shipment.service

import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.dto.ForwardRecipientRequest
import com.pavex.shipment.dto.ShipmentExceptionStateResponse
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentForbiddenException
import com.pavex.shipment.repository.ShipmentExceptionRequestRepository
import com.pavex.shipment.repository.ShipmentRepository
import com.pavex.shipment.repository.ShipmentWeightAdjustmentRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Authorization boundary for administrative shipment exception workflows.
 * The domain workflow remains focused on state transitions; every controller
 * call goes through this service so read/write scope is checked before a UUID
 * can reach the unscoped workflow.
 */
@Service
class ScopedShipmentExceptionWorkflowService(
    private val workflow: ShipmentExceptionWorkflowService,
    private val shipments: ShipmentRepository,
    private val requests: ShipmentExceptionRequestRepository,
    private val adjustments: ShipmentWeightAdjustmentRepository,
    private val access: ShipmentExceptionAccessService,
) {
    @Transactional(readOnly = true)
    fun get(authentication: Authentication, shipmentId: UUID): ShipmentExceptionStateResponse {
        val scope = access.resolveRead(authentication)
        val shipment = load(shipmentId)
        ensureShipmentVisible(scope, shipment)
        return filterState(scope, workflow.get(shipmentId))
    }

    @Transactional
    fun placeHold(authentication: Authentication, shipmentId: UUID, reason: String, requestedHubId: UUID?): ShipmentExceptionStateResponse {
        val scope = access.resolveManage(authentication)
        val shipment = load(shipmentId)
        val hubId = resolveActionHub(scope, shipment, requestedHubId)
        return filterState(scope, workflow.placeHold(shipmentId, scope.actorUserId, reason, hubId))
    }

    @Transactional
    fun releaseHold(authentication: Authentication, shipmentId: UUID, reason: String, requestedHubId: UUID?): ShipmentExceptionStateResponse {
        val scope = access.resolveManage(authentication)
        val shipment = load(shipmentId)
        val protectedHubId = shipment.holdHubId ?: requestedHubId
        requireScopedHub(scope, protectedHubId)
        if (requestedHubId != null && protectedHubId != requestedHubId) {
            throw ShipmentForbiddenException("exception_hub_forbidden", "Hold belongs to another hub scope.")
        }
        return filterState(scope, workflow.releaseHold(shipmentId, scope.actorUserId, reason, protectedHubId))
    }

    @Transactional
    fun requestReturn(authentication: Authentication, shipmentId: UUID, reason: String, requestedHubId: UUID?) =
        request(authentication, shipmentId, requestedHubId) { actor, hubId -> workflow.requestReturn(shipmentId, actor, reason, hubId) }

    @Transactional
    fun requestCancellation(authentication: Authentication, shipmentId: UUID, reason: String, requestedHubId: UUID?) =
        request(authentication, shipmentId, requestedHubId) { actor, hubId -> workflow.requestCancellation(shipmentId, actor, reason, hubId) }

    @Transactional
    fun requestForward(
        authentication: Authentication,
        shipmentId: UUID,
        reason: String,
        recipient: ForwardRecipientRequest,
        requestedHubId: UUID?,
    ) = request(authentication, shipmentId, requestedHubId) { actor, hubId ->
        workflow.requestForward(shipmentId, actor, reason, recipient, hubId)
    }

    @Transactional
    fun review(
        authentication: Authentication,
        shipmentId: UUID,
        requestId: UUID,
        approve: Boolean,
        note: String?,
    ): ShipmentExceptionStateResponse {
        val scope = access.resolveManage(authentication)
        val request = requests.findByIdAndShipmentId(requestId, shipmentId)
            ?: throw com.pavex.shipment.exception.ShipmentNotFoundException("exception_request_not_found", "Shipment exception request was not found.")
        requireScopedHub(scope, request.hubId)
        return filterState(scope, workflow.review(shipmentId, requestId, scope.actorUserId, approve, note))
    }

    @Transactional
    fun completeCancellation(
        authentication: Authentication,
        shipmentId: UUID,
        requestId: UUID,
        note: String?,
    ): ShipmentExceptionStateResponse {
        val scope = access.resolveManage(authentication)
        val request = requests.findByIdAndShipmentId(requestId, shipmentId)
            ?: throw com.pavex.shipment.exception.ShipmentNotFoundException("cancellation_request_not_found", "Cancellation request was not found.")
        requireScopedHub(scope, request.hubId)
        return filterState(scope, workflow.completeCancellation(shipmentId, requestId, scope.actorUserId, note))
    }

    @Transactional
    fun completeRoutingRequest(
        authentication: Authentication,
        shipmentId: UUID,
        requestId: UUID,
        note: String?,
    ): ShipmentExceptionStateResponse {
        val scope = access.resolveManage(authentication)
        val request = requests.findByIdAndShipmentId(requestId, shipmentId)
            ?: throw com.pavex.shipment.exception.ShipmentNotFoundException("exception_request_not_found", "Shipment exception request was not found.")
        requireScopedHub(scope, request.hubId)
        return filterState(scope, workflow.completeRoutingRequest(shipmentId, requestId, scope.actorUserId, note))
    }

    @Transactional
    fun adjustWeight(
        authentication: Authentication,
        shipmentId: UUID,
        parcelId: UUID,
        actualWeightKg: BigDecimal,
        reason: String,
        requestedHubId: UUID?,
    ): ShipmentExceptionStateResponse {
        val scope = access.resolveWeightAdjust(authentication)
        val shipment = load(shipmentId)
        val parcel = shipment.parcels.singleOrNull { it.id == parcelId }
            ?: throw InvalidShipmentRequestException("parcel_not_found", "Parcel does not belong to this shipment.")
        val hubId = if (parcel.currentHubId != null) {
            val currentHub = requireNotNull(parcel.currentHubId)
            if (requestedHubId != null && requestedHubId != currentHub) {
                throw ShipmentForbiddenException("exception_hub_forbidden", "Weight adjustment belongs to another hub scope.")
            }
            requireScopedHub(scope, currentHub)
            currentHub
        } else {
            resolveActionHub(scope, shipment, requestedHubId)
        }
        return filterState(
            scope,
            workflow.adjustWeight(shipmentId, parcelId, scope.actorUserId, actualWeightKg, reason, hubId),
        )
    }

    @Transactional
    fun completeWeightReview(
        authentication: Authentication,
        shipmentId: UUID,
        adjustmentId: UUID,
        note: String,
    ): ShipmentExceptionStateResponse {
        val scope = access.resolveManage(authentication)
        val adjustment = adjustments.findByIdAndShipmentId(adjustmentId, shipmentId)
            ?: throw com.pavex.shipment.exception.ShipmentNotFoundException("weight_adjustment_not_found", "Weight adjustment was not found.")
        requireScopedHub(scope, adjustment.hubId)
        return filterState(scope, workflow.completeWeightReview(shipmentId, adjustmentId, scope.actorUserId, note))
    }

    private fun request(
        authentication: Authentication,
        shipmentId: UUID,
        requestedHubId: UUID?,
        action: (UUID, UUID?) -> ShipmentExceptionStateResponse,
    ): ShipmentExceptionStateResponse {
        val scope = access.resolveRequest(authentication)
        val shipment = load(shipmentId)
        val hubId = resolveActionHub(scope, shipment, requestedHubId)
        return filterState(scope, action(scope.actorUserId, hubId))
    }

    private fun resolveActionHub(scope: HubAccessScope, shipment: Shipment, requestedHubId: UUID?): UUID? {
        val related = relatedHubIds(shipment)
        if (requestedHubId != null) {
            if (!scope.canAccessHub(requestedHubId) || (related.isNotEmpty() && requestedHubId !in related)) {
                throw ShipmentForbiddenException("exception_hub_forbidden", "Hub is outside the allowed shipment scope.")
            }
            return requestedHubId
        }
        if (scope.isGlobal) return related.singleOrNull()
        val accessible = related.intersect(scope.hubIds)
        if (accessible.size == 1) return accessible.single()
        if (accessible.isEmpty()) {
            throw ShipmentForbiddenException("exception_hub_forbidden", "Shipment is outside the allowed hub scope.")
        }
        throw InvalidShipmentRequestException(
            "exception_hub_required",
            "hubId is required because this shipment is currently associated with multiple allowed hubs.",
        )
    }

    private fun ensureShipmentVisible(scope: HubAccessScope, shipment: Shipment) {
        if (scope.isGlobal) return
        val visibleByCurrentHub = relatedHubIds(shipment).any(scope.hubIds::contains)
        val visibleByRequest = requests.findAllByShipmentIdOrderByRequestedAtAsc(shipment.id)
            .any { it.hubId != null && it.hubId in scope.hubIds }
        val visibleByWeight = adjustments.findAllByShipmentIdOrderByAdjustedAtAsc(shipment.id)
            .any { it.hubId != null && it.hubId in scope.hubIds }
        if (!visibleByCurrentHub && !visibleByRequest && !visibleByWeight) {
            throw ShipmentForbiddenException("exception_hub_forbidden", "Shipment is outside the allowed hub scope.")
        }
    }

    private fun relatedHubIds(shipment: Shipment): Set<UUID> = buildSet {
        shipment.holdHubId?.let(::add)
        shipment.parcels.mapNotNullTo(this) { it.currentHubId }
        if (isNotEmpty()) return@buildSet

        shipment.events.filter { it.hubId != null }
            .maxWithOrNull(compareBy({ it.occurredAt }, { it.id }))
            ?.hubId
            ?.let(::add)
        if (isNotEmpty()) return@buildSet

        when (shipment.status) {
            ShipmentStatus.AWAITING_PICKUP,
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.AT_ORIGIN_HUB,
            ShipmentStatus.IN_TRANSIT,
            -> add(shipment.originHubId)

            ShipmentStatus.AT_DESTINATION_HUB,
            ShipmentStatus.OUT_FOR_DELIVERY,
            ShipmentStatus.DELIVERY_FAILED,
            ShipmentStatus.PARTIALLY_DELIVERED,
            ShipmentStatus.DELIVERED,
            -> add(shipment.destinationHubId)

            ShipmentStatus.RETURNING,
            ShipmentStatus.RETURNED,
            ShipmentStatus.CANCELLED,
            -> Unit
        }
    }

    private fun requireScopedHub(scope: HubAccessScope, hubId: UUID?) {
        if (scope.isGlobal) return
        if (hubId == null || !scope.canAccessHub(hubId)) {
            throw ShipmentForbiddenException("exception_hub_forbidden", "Exception belongs to another hub scope.")
        }
    }

    private fun filterState(scope: HubAccessScope, state: ShipmentExceptionStateResponse): ShipmentExceptionStateResponse {
        if (scope.isGlobal) return state
        val holdVisible = state.holdHubId?.let(scope::canAccessHub) == true
        return state.copy(
            isOnHold = state.isOnHold && holdVisible,
            holdReason = state.holdReason.takeIf { holdVisible },
            holdActorUserId = state.holdActorUserId.takeIf { holdVisible },
            holdHubId = state.holdHubId.takeIf { holdVisible },
            heldAt = state.heldAt.takeIf { holdVisible },
            returnRequest = state.returnRequest?.takeIf { it.hubId?.let(scope::canAccessHub) == true },
            forwardRequest = state.forwardRequest?.takeIf { it.hubId?.let(scope::canAccessHub) == true },
            cancellationRequest = state.cancellationRequest?.takeIf { it.hubId?.let(scope::canAccessHub) == true },
            weightAdjustments = state.weightAdjustments.filter { it.hubId?.let(scope::canAccessHub) == true },
            hasPendingWeightReview = state.weightAdjustments.any {
                !it.reviewCompleted && it.hubId?.let(scope::canAccessHub) == true
            },
        )
    }

    private fun load(shipmentId: UUID): Shipment = shipments.findById(shipmentId).orElseThrow {
        com.pavex.shipment.exception.ShipmentNotFoundException("shipment_not_found", "Shipment was not found.")
    }
}
