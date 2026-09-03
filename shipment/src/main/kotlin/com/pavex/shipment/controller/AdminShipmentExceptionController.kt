package com.pavex.shipment.controller

import com.pavex.shipment.dto.CompleteShipmentExceptionRequest
import com.pavex.shipment.dto.ShipmentExceptionRequestBody
import com.pavex.shipment.dto.ShipmentExceptionReviewRequest
import com.pavex.shipment.dto.ShipmentExceptionStateResponse
import com.pavex.shipment.dto.ShipmentForwardRequestBody
import com.pavex.shipment.dto.ShipmentHoldRequest
import com.pavex.shipment.dto.ShipmentWeightAdjustmentRequest
import com.pavex.shipment.dto.ShipmentWeightReviewRequest
import com.pavex.shipment.service.ScopedShipmentExceptionWorkflowService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/shipments/{shipmentId}")
class AdminShipmentExceptionController(
    private val workflow: ScopedShipmentExceptionWorkflowService,
) {
    @GetMapping("/exceptions")
    @PreAuthorize("hasAnyAuthority('shipments.exceptions.read','shipments.exceptions.manage')")
    fun get(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
    ): ResponseEntity<ShipmentExceptionStateResponse> = ResponseEntity.ok(workflow.get(authentication, shipmentId))

    @PostMapping("/hold")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun hold(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentHoldRequest,
    ) = ResponseEntity.ok(workflow.placeHold(authentication, shipmentId, request.reason, request.hubId))

    @PostMapping("/hold/release")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun releaseHold(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentHoldRequest,
    ) = ResponseEntity.ok(workflow.releaseHold(authentication, shipmentId, request.reason, request.hubId))

    @PostMapping("/return-requests")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun requestReturn(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentExceptionRequestBody,
    ) = ResponseEntity.ok(workflow.requestReturn(authentication, shipmentId, request.reason, request.hubId))

    @PostMapping("/return-requests/{requestId}/review")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun reviewReturn(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: ShipmentExceptionReviewRequest,
    ) = ResponseEntity.ok(workflow.review(authentication, shipmentId, requestId, request.approve, request.note))

    @PostMapping("/return-requests/{requestId}/complete")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun completeReturn(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: CompleteShipmentExceptionRequest,
    ) = ResponseEntity.ok(workflow.completeRoutingRequest(authentication, shipmentId, requestId, request.note))

    @PostMapping("/forward-requests")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun requestForward(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentForwardRequestBody,
    ) = ResponseEntity.ok(
        workflow.requestForward(authentication, shipmentId, request.reason, request.recipient, request.hubId),
    )

    @PostMapping("/forward-requests/{requestId}/review")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun reviewForward(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: ShipmentExceptionReviewRequest,
    ) = ResponseEntity.ok(workflow.review(authentication, shipmentId, requestId, request.approve, request.note))

    @PostMapping("/forward-requests/{requestId}/complete")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun completeForward(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: CompleteShipmentExceptionRequest,
    ) = ResponseEntity.ok(workflow.completeRoutingRequest(authentication, shipmentId, requestId, request.note))

    @PostMapping("/cancellation-requests")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun requestCancellation(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentExceptionRequestBody,
    ) = ResponseEntity.ok(workflow.requestCancellation(authentication, shipmentId, request.reason, request.hubId))

    @PostMapping("/cancellation-requests/{requestId}/review")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun reviewCancellation(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: ShipmentExceptionReviewRequest,
    ) = ResponseEntity.ok(workflow.review(authentication, shipmentId, requestId, request.approve, request.note))

    @PostMapping("/cancellation-requests/{requestId}/complete")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun completeCancellation(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: CompleteShipmentExceptionRequest,
    ) = ResponseEntity.ok(workflow.completeCancellation(authentication, shipmentId, requestId, request.note))

    @PostMapping("/weight-adjustments")
    @PreAuthorize("hasAuthority('shipments.weight.adjust')")
    fun adjustWeight(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentWeightAdjustmentRequest,
    ) = ResponseEntity.ok(
        workflow.adjustWeight(
            authentication,
            shipmentId,
            request.parcelId,
            request.actualWeightKg,
            request.reason,
            request.hubId,
        ),
    )

    @PostMapping("/weight-adjustments/{adjustmentId}/review")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun reviewWeight(
        authentication: Authentication,
        @PathVariable shipmentId: UUID,
        @PathVariable adjustmentId: UUID,
        @Valid @RequestBody request: ShipmentWeightReviewRequest,
    ) = ResponseEntity.ok(workflow.completeWeightReview(authentication, shipmentId, adjustmentId, request.note))
}
