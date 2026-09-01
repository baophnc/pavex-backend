package com.pavex.shipment.controller

import com.pavex.shipment.dto.CompleteShipmentExceptionRequest
import com.pavex.shipment.dto.ShipmentExceptionRequestBody
import com.pavex.shipment.dto.ShipmentExceptionReviewRequest
import com.pavex.shipment.dto.ShipmentExceptionStateResponse
import com.pavex.shipment.dto.ShipmentForwardRequestBody
import com.pavex.shipment.dto.ShipmentHoldRequest
import com.pavex.shipment.dto.ShipmentWeightAdjustmentRequest
import com.pavex.shipment.dto.ShipmentWeightReviewRequest
import com.pavex.shipment.service.ShipmentExceptionWorkflowService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
    private val workflow: ShipmentExceptionWorkflowService,
) {
    @GetMapping("/exceptions")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun get(@PathVariable shipmentId: UUID): ResponseEntity<ShipmentExceptionStateResponse> = ResponseEntity.ok(workflow.get(shipmentId))

    @PostMapping("/hold")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun hold(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentHoldRequest,
    ) = ResponseEntity.ok(workflow.placeHold(shipmentId, UUID.fromString(jwt.subject), request.reason, request.hubId))

    @PostMapping("/hold/release")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun releaseHold(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentHoldRequest,
    ) = ResponseEntity.ok(workflow.releaseHold(shipmentId, UUID.fromString(jwt.subject), request.reason, request.hubId))

    @PostMapping("/return-requests")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun requestReturn(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentExceptionRequestBody,
    ) = ResponseEntity.ok(workflow.requestReturn(shipmentId, UUID.fromString(jwt.subject), request.reason))

    @PostMapping("/return-requests/{requestId}/review")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun reviewReturn(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: ShipmentExceptionReviewRequest,
    ) = ResponseEntity.ok(workflow.review(shipmentId, requestId, UUID.fromString(jwt.subject), request.approve, request.note))

    @PostMapping("/return-requests/{requestId}/complete")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun completeReturn(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: CompleteShipmentExceptionRequest,
    ) = ResponseEntity.ok(workflow.completeRoutingRequest(shipmentId, requestId, UUID.fromString(jwt.subject), request.note))

    @PostMapping("/forward-requests")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun requestForward(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentForwardRequestBody,
    ) = ResponseEntity.ok(workflow.requestForward(shipmentId, UUID.fromString(jwt.subject), request.reason, request.recipient))

    @PostMapping("/forward-requests/{requestId}/review")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun reviewForward(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: ShipmentExceptionReviewRequest,
    ) = ResponseEntity.ok(workflow.review(shipmentId, requestId, UUID.fromString(jwt.subject), request.approve, request.note))

    @PostMapping("/forward-requests/{requestId}/complete")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun completeForward(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: CompleteShipmentExceptionRequest,
    ) = ResponseEntity.ok(workflow.completeRoutingRequest(shipmentId, requestId, UUID.fromString(jwt.subject), request.note))

    @PostMapping("/cancellation-requests")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun requestCancellation(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentExceptionRequestBody,
    ) = ResponseEntity.ok(workflow.requestCancellation(shipmentId, UUID.fromString(jwt.subject), request.reason))

    @PostMapping("/cancellation-requests/{requestId}/review")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun reviewCancellation(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: ShipmentExceptionReviewRequest,
    ) = ResponseEntity.ok(workflow.review(shipmentId, requestId, UUID.fromString(jwt.subject), request.approve, request.note))

    @PostMapping("/cancellation-requests/{requestId}/complete")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun completeCancellation(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: CompleteShipmentExceptionRequest,
    ) = ResponseEntity.ok(workflow.completeCancellation(shipmentId, requestId, UUID.fromString(jwt.subject), request.note))

    @PostMapping("/weight-adjustments")
    @PreAuthorize("hasAuthority('shipments.weight.adjust')")
    fun adjustWeight(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @Valid @RequestBody request: ShipmentWeightAdjustmentRequest,
    ) = ResponseEntity.ok(
        workflow.adjustWeight(
            shipmentId,
            request.parcelId,
            UUID.fromString(jwt.subject),
            request.actualWeightKg,
            request.reason,
            request.hubId,
        ),
    )

    @PostMapping("/weight-adjustments/{adjustmentId}/review")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun reviewWeight(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable shipmentId: UUID,
        @PathVariable adjustmentId: UUID,
        @Valid @RequestBody request: ShipmentWeightReviewRequest,
    ) = ResponseEntity.ok(workflow.completeWeightReview(shipmentId, adjustmentId, UUID.fromString(jwt.subject), request.note))
}
