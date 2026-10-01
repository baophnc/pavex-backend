package com.pavex.shipment.controller

import com.pavex.shipment.repository.ShipmentRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ShipmentCodesRequest(@field:Size(max = 200) val ids: List<UUID>)

data class ShipmentCodeResponse(val id: UUID, val trackingCode: String)

/**
 * Tracking codes behind shipment ids. Operations knows shipments only by id, so its job and
 * dispatch screens ask here to print "PAVEX26…" instead. Nothing but the code is returned.
 */
@RestController
@RequestMapping("/api/v1/admin/shipments/codes")
class ShipmentCodeController(private val shipments: ShipmentRepository) {
    @PostMapping
    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyAuthority('shipments.admin.read','shipments.read-hub','scope.all-hubs','operations.hub.read','assignments.read-own','billing.cash.read-hub')",
    )
    fun codes(@Valid @RequestBody request: ShipmentCodesRequest): List<ShipmentCodeResponse> {
        val ids = request.ids.toSet()
        if (ids.isEmpty()) return emptyList()
        return shipments.findTrackingCodes(ids).map { ShipmentCodeResponse(it.id, it.trackingCode) }
    }
}
