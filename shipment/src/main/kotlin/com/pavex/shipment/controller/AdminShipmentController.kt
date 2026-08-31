package com.pavex.shipment.controller

import com.pavex.shipment.dto.AdminShipmentDetailResponse
import com.pavex.shipment.dto.AdminShipmentPageResponse
import com.pavex.shipment.service.AdminShipmentQueryService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/shipments")
class AdminShipmentController(
    private val queries: AdminShipmentQueryService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('shipments.admin.read')")
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) lifecycleStatus: String?,
        @RequestParam(required = false) merchantId: UUID?,
        @RequestParam(name = "q", required = false) query: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<AdminShipmentPageResponse> = ResponseEntity.ok(
        queries.list(status, lifecycleStatus, merchantId, query, cursor, limit),
    )

    @GetMapping("/{trackingCode}")
    @PreAuthorize("hasAuthority('shipments.admin.read')")
    fun get(@PathVariable trackingCode: String): ResponseEntity<AdminShipmentDetailResponse> =
        ResponseEntity.ok(queries.get(trackingCode))
}
