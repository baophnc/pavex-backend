package com.pavex.shipment.controller

import com.pavex.common.api.PageResponse
import com.pavex.shipment.dto.ShipmentControlAlertResponse
import com.pavex.shipment.dto.ShipmentControlAlertSeverity
import com.pavex.shipment.dto.ShipmentControlAlertType
import com.pavex.shipment.dto.ShipmentControlTowerSummaryResponse
import com.pavex.shipment.service.ShipmentControlTowerService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/control-tower")
class ShipmentControlTowerController(
    private val service: ShipmentControlTowerService,
) {
    @GetMapping("/alerts")
    fun alerts(
        authentication: Authentication,
        @RequestParam(required = false) type: ShipmentControlAlertType?,
        @RequestParam(required = false) severity: ShipmentControlAlertSeverity?,
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): PageResponse<ShipmentControlAlertResponse> =
        service.search(authentication, type, severity, hubId, search, page, pageSize)

    @GetMapping("/summary")
    fun summary(
        authentication: Authentication,
        @RequestParam(required = false) hubId: UUID?,
    ): ShipmentControlTowerSummaryResponse = service.summary(authentication, hubId)
}
