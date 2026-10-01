package com.pavex.shipment.controller

import com.pavex.shipment.dto.AdminShipmentDetailResponse
import com.pavex.shipment.dto.AdminShipmentPageResponse
import com.pavex.shipment.dto.AdminUpdateShipmentRequest
import com.pavex.shipment.service.AdminShipmentAccessService
import com.pavex.shipment.service.AdminShipmentQueryService
import com.pavex.shipment.service.UpdateShipmentService
import com.pavex.shipment.security.requirePavexActor
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/shipments")
class AdminShipmentController(
    private val queries: AdminShipmentQueryService,
    private val access: AdminShipmentAccessService,
    private val updates: UpdateShipmentService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('shipments.admin.read','shipments.read-hub')")
    fun list(
        authentication: Authentication,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) lifecycleStatus: String?,
        @RequestParam(required = false) merchantId: UUID?,
        @RequestParam(name = "q", required = false) query: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(required = false) createdFrom: LocalDate?,
        @RequestParam(required = false) createdTo: LocalDate?,
    ): ResponseEntity<AdminShipmentPageResponse> = ResponseEntity.ok(
        queries.list(
            access.resolve(authentication), status, lifecycleStatus, merchantId, query, cursor, limit,
            hubId, createdFrom, createdTo,
        ),
    )

    @GetMapping("/{trackingCode}")
    @PreAuthorize("hasAnyAuthority('shipments.admin.read','shipments.read-hub')")
    fun get(authentication: Authentication, @PathVariable trackingCode: String): ResponseEntity<AdminShipmentDetailResponse> =
        ResponseEntity.ok(queries.get(access.resolve(authentication), trackingCode))

    /** Corrects the recipient's contact details or the note, also after pickup; the reason goes on the timeline. */
    @PatchMapping("/{trackingCode}")
    @PreAuthorize("hasAuthority('shipments.admin.update')")
    fun update(
        authentication: Authentication,
        @PathVariable trackingCode: String,
        @Valid @RequestBody request: AdminUpdateShipmentRequest,
    ): ResponseEntity<AdminShipmentDetailResponse> = ResponseEntity.ok(
        updates.updateAsStaff(
            access.resolve(authentication),
            authentication.requirePavexActor().userId,
            trackingCode,
            request,
        ),
    )
}
