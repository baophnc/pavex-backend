package com.pavex.shipment.controller

import com.pavex.common.api.PageResponse
import com.pavex.shipment.dto.ShipmentExceptionQueueItemResponse
import com.pavex.shipment.dto.ShipmentExceptionQueueKind
import com.pavex.shipment.dto.ShipmentExceptionQueueStatus
import com.pavex.shipment.dto.ShipmentExceptionQueueSummaryResponse
import com.pavex.shipment.service.ShipmentExceptionQueueService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/shipment-exceptions")
class AdminShipmentExceptionQueueController(
    private val queue: ShipmentExceptionQueueService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun list(
        @RequestParam(required = false) kind: ShipmentExceptionQueueKind?,
        @RequestParam(required = false) status: ShipmentExceptionQueueStatus?,
        @RequestParam(name = "q", required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): ResponseEntity<PageResponse<ShipmentExceptionQueueItemResponse>> = ResponseEntity.ok(
        queue.search(kind, status, search, page, pageSize),
    )

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('shipments.exceptions.manage')")
    fun summary(): ResponseEntity<ShipmentExceptionQueueSummaryResponse> = ResponseEntity.ok(queue.summary())
}
