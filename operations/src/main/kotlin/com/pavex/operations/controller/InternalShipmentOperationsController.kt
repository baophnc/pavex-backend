package com.pavex.operations.controller

import com.pavex.contracts.operations.CancelShipmentOperationsRequest
import com.pavex.contracts.operations.OperationsAccessCheckRequest
import com.pavex.contracts.operations.OperationsAccessCheckResponse
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.PlanShipmentOperationsRequest
import com.pavex.contracts.operations.ShipmentAssignmentAccessRequest
import com.pavex.contracts.operations.ShipmentAssignmentCompletionRequest
import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.contracts.operations.ShipmentOperationsAssignmentSnapshot
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.contracts.operations.ShipmentOperationsPlanResponse
import com.pavex.operations.dto.OperationalAssignmentResponse
import com.pavex.operations.dto.ShipmentAssignmentPlan
import com.pavex.operations.dto.ShipmentAssignmentRouteLeg
import com.pavex.operations.entity.OperationalAssignmentType
import com.pavex.operations.service.OperationalAssignmentService
import com.pavex.operations.service.ShipmentCaseAdminScopeService
import com.pavex.operations.service.ShipmentOperationsAccessService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/operations/shipments")
class InternalShipmentOperationsController(
    private val assignments: OperationalAssignmentService,
    private val access: ShipmentOperationsAccessService,
    private val caseAdminScope: ShipmentCaseAdminScopeService,
) {
    @PostMapping("/{shipmentId}/plan")
    fun plan(
        @PathVariable shipmentId: UUID,
        @RequestBody request: PlanShipmentOperationsRequest,
    ): ShipmentOperationsPlanResponse {
        val planned = assignments.plan(
            ShipmentAssignmentPlan(
                shipmentId = shipmentId,
                originHubId = request.originHubId,
                destinationHubId = request.destinationHubId,
                routeLegs = request.routeLegs.map {
                    ShipmentAssignmentRouteLeg(it.routeLegId, it.sequence, it.fromHubId, it.toHubId)
                },
                priority = request.priority,
            ),
        )
        return ShipmentOperationsPlanResponse(shipmentId, planned.map(::snapshot))
    }

    @PostMapping("/{shipmentId}/cancel")
    fun cancel(
        @PathVariable shipmentId: UUID,
        @RequestBody request: CancelShipmentOperationsRequest,
    ): ResponseEntity<Void> {
        assignments.cancelShipment(shipmentId, request.reason)
        return ResponseEntity.noContent().build()
    }

    /** Open assignments of one worker, for the shipment-side field app views. */
    @GetMapping("/assignments/by-user/{userId}")
    fun userAssignments(@PathVariable userId: UUID): List<WorkforceAssignmentSnapshot> =
        assignments.mine(userId).map { value ->
            WorkforceAssignmentSnapshot(
                id = value.id,
                code = value.code,
                shipmentId = value.shipmentId,
                sequence = value.sequence,
                routeLegId = value.routeLegId,
                type = OperationsAssignmentType.valueOf(value.type.name),
                hubId = value.hubId,
                status = com.pavex.contracts.operations.OperationsAssignmentStatus.valueOf(value.status.name),
                priority = value.priority,
                assignedAt = value.assignedAt,
                acceptedAt = value.acceptedAt,
                startedAt = value.startedAt,
            )
        }

    @GetMapping("/case-admin-scope/{userId}")
    fun caseAdminScope(@PathVariable userId: UUID): ShipmentCaseAdminScopeSnapshot =
        caseAdminScope.scope(userId)

    @PostMapping("/access/check")
    fun checkAccess(@RequestBody request: OperationsAccessCheckRequest): OperationsAccessCheckResponse =
        OperationsAccessCheckResponse(
            access.canOperateAtHub(request.userId, request.hubId, request.capability),
        )

    @PostMapping("/{shipmentId}/assignments/{assignmentId}/authorize")
    fun authorize(
        @PathVariable shipmentId: UUID,
        @PathVariable assignmentId: UUID,
        @RequestBody request: ShipmentAssignmentAccessRequest,
    ): ShipmentOperationsAssignmentSnapshot = snapshot(
        assignments.authorizeFromShipment(shipmentId, assignmentId, request),
    )

    /** A hub scan takes over hub handling that nobody accepted yet; see the service for the rules. */
    @PostMapping("/{shipmentId}/assignments/{assignmentId}/claim-handling")
    fun claimHandling(
        @PathVariable shipmentId: UUID,
        @PathVariable assignmentId: UUID,
        @RequestBody request: ShipmentAssignmentAccessRequest,
    ): ShipmentOperationsAssignmentSnapshot = snapshot(
        assignments.claimHubHandlingFromShipment(shipmentId, assignmentId, request),
    )

    @PostMapping("/{shipmentId}/assignments/{assignmentId}/complete")
    fun complete(
        @PathVariable shipmentId: UUID,
        @PathVariable assignmentId: UUID,
        @RequestBody request: ShipmentAssignmentCompletionRequest,
    ): ShipmentOperationsAssignmentSnapshot = snapshot(
        assignments.completeFromShipment(shipmentId, assignmentId, request),
    )

    @GetMapping("/{shipmentId}/route-legs/{routeLegId}/assignment")
    fun routeLegAssignment(
        @PathVariable shipmentId: UUID,
        @PathVariable routeLegId: UUID,
    ): ResponseEntity<ShipmentOperationsAssignmentSnapshot> =
        assignments.findRouteLegAssignment(shipmentId, routeLegId)
            ?.let { ResponseEntity.ok(snapshot(it)) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/{shipmentId}/types/{type}/hubs/{hubId}/assignment")
    fun typeAssignment(
        @PathVariable shipmentId: UUID,
        @PathVariable type: OperationsAssignmentType,
        @PathVariable hubId: UUID,
    ): ResponseEntity<ShipmentOperationsAssignmentSnapshot> =
        assignments.findTypeAssignment(shipmentId, OperationalAssignmentType.valueOf(type.name), hubId)
            ?.let { ResponseEntity.ok(snapshot(it)) }
            ?: ResponseEntity.notFound().build()

    private fun snapshot(value: OperationalAssignmentResponse) = ShipmentOperationsAssignmentSnapshot(
        id = value.id,
        shipmentId = value.shipmentId,
        sequence = value.sequence,
        routeLegId = value.routeLegId,
        type = OperationsAssignmentType.valueOf(value.type.name),
        hubId = value.hubId,
        assigneeWorkforceMemberId = value.assigneeWorkforceMemberId,
        status = com.pavex.contracts.operations.OperationsAssignmentStatus.valueOf(value.status.name),
    )
}
