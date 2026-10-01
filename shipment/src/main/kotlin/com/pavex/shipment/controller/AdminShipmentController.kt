package com.pavex.shipment.controller

import com.pavex.shipment.dto.AdminShipmentDetailResponse
import com.pavex.shipment.dto.AdminShipmentPageResponse
import com.pavex.shipment.dto.ShipmentLabelsResponse
import com.pavex.shipment.service.AdminShipmentAccessService
import com.pavex.shipment.service.AdminShipmentQueryService
import java.util.UUID
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/shipments")
class AdminShipmentController(
    private val queries: AdminShipmentQueryService,
    private val access: AdminShipmentAccessService,
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
    ): ResponseEntity<AdminShipmentPageResponse> = ResponseEntity.ok(
        queries.list(access.resolve(authentication), status, lifecycleStatus, merchantId, query, cursor, limit),
    )

    @GetMapping("/{trackingCode}/labels")
    @PreAuthorize("hasAnyAuthority('shipments.admin.read','shipments.read-hub')")
    fun labels(authentication: Authentication, @PathVariable trackingCode: String): ResponseEntity<ShipmentLabelsResponse> =
        ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(queries.labels(access.resolve(authentication), trackingCode))

    @GetMapping("/{trackingCode}")
    @PreAuthorize("hasAnyAuthority('shipments.admin.read','shipments.read-hub')")
    fun get(authentication: Authentication, @PathVariable trackingCode: String): ResponseEntity<AdminShipmentDetailResponse> =
        ResponseEntity.ok(queries.get(access.resolve(authentication), trackingCode))
}
