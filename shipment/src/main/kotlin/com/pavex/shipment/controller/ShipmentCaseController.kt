package com.pavex.shipment.controller

import com.pavex.common.api.PageResponse
import com.pavex.shipment.dto.AddShipmentCaseNoteRequest
import com.pavex.shipment.dto.AssignShipmentCaseRequest
import com.pavex.shipment.dto.CreateProblemParcelCaseRequest
import com.pavex.shipment.dto.CreateUnidentifiedParcelCaseRequest
import com.pavex.shipment.dto.LinkShipmentCaseRequest
import com.pavex.shipment.dto.ResolveShipmentCaseRequest
import com.pavex.shipment.dto.ShipmentCaseMonitoringSummary
import com.pavex.shipment.dto.ShipmentCaseNoteRequest
import com.pavex.shipment.dto.ShipmentCaseResponse
import com.pavex.shipment.dto.ShipmentCaseSummaryResponse
import com.pavex.shipment.entity.ShipmentCaseSeverity
import com.pavex.shipment.entity.ShipmentCaseStatus
import com.pavex.shipment.entity.ShipmentCaseType
import com.pavex.shipment.service.ShipmentCaseService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/shipment-cases")
@PreAuthorize("hasAnyAuthority('shipments.cases.manage','operations.hub.manage')")
class ShipmentCaseController(
    private val service: ShipmentCaseService,
) {
    @GetMapping
    fun search(
        authentication: Authentication,
        @RequestParam(required = false) type: ShipmentCaseType?,
        @RequestParam(required = false) status: ShipmentCaseStatus?,
        @RequestParam(required = false) severity: ShipmentCaseSeverity?,
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(required = false) assignedToUserId: UUID?,
        @RequestParam(required = false) shipmentId: UUID?,
        @RequestParam(name = "q", required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): ResponseEntity<PageResponse<ShipmentCaseSummaryResponse>> = ResponseEntity.ok(
        service.search(authentication, type, status, severity, hubId, assignedToUserId, shipmentId, search, page, pageSize),
    )

    @GetMapping("/summary")
    fun summary(
        authentication: Authentication,
        @RequestParam(required = false) hubId: UUID?,
    ): ResponseEntity<ShipmentCaseMonitoringSummary> = ResponseEntity.ok(service.monitoringSummary(authentication, hubId))

    @GetMapping("/{caseId}")
    fun get(authentication: Authentication, @PathVariable caseId: UUID): ResponseEntity<ShipmentCaseResponse> =
        ResponseEntity.ok(service.get(authentication, caseId))

    @PostMapping("/problem-parcels")
    fun createProblem(
        authentication: Authentication,
        @Valid @RequestBody request: CreateProblemParcelCaseRequest,
    ): ResponseEntity<ShipmentCaseResponse> {
        val created = service.createProblem(
            authentication,
            request.shipmentId,
            request.parcelId,
            request.hubId,
            request.severity,
            request.title,
            request.description,
        )
        return ResponseEntity.created(URI.create("/api/v1/admin/shipment-cases/${created.id}")).body(created)
    }

    @PostMapping("/unidentified-parcels")
    fun createUnidentified(
        authentication: Authentication,
        @Valid @RequestBody request: CreateUnidentifiedParcelCaseRequest,
    ): ResponseEntity<ShipmentCaseResponse> {
        val created = service.createUnidentified(
            authentication,
            request.hubId,
            request.severity,
            request.title,
            request.description,
            request.externalBarcode,
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/admin/shipment-cases/${created.id}"))
            .body(created)
    }

    @PostMapping("/{caseId}/assign")
    fun assign(
        authentication: Authentication,
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: AssignShipmentCaseRequest,
    ) = ResponseEntity.ok(service.assign(authentication, caseId, request.assignedToUserId, request.note))

    @PostMapping("/{caseId}/investigate")
    fun investigate(
        authentication: Authentication,
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: ShipmentCaseNoteRequest,
    ) = ResponseEntity.ok(service.investigate(authentication, caseId, request.note))

    @PostMapping("/{caseId}/notes")
    fun addNote(
        authentication: Authentication,
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: AddShipmentCaseNoteRequest,
    ) = ResponseEntity.ok(service.addNote(authentication, caseId, request.note))

    @PostMapping("/{caseId}/link")
    fun link(
        authentication: Authentication,
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: LinkShipmentCaseRequest,
    ) = ResponseEntity.ok(service.link(authentication, caseId, request.shipmentId, request.parcelId, request.note))

    @PostMapping("/{caseId}/resolve")
    fun resolve(
        authentication: Authentication,
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: ResolveShipmentCaseRequest,
    ) = ResponseEntity.ok(service.resolve(authentication, caseId, request.resolution))

    @PostMapping("/{caseId}/close")
    fun close(
        authentication: Authentication,
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: ShipmentCaseNoteRequest,
    ) = ResponseEntity.ok(service.close(authentication, caseId, request.note))
}
