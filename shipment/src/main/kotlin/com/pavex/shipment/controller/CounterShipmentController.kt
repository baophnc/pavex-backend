package com.pavex.shipment.controller

import com.pavex.common.http.PavexHeaders
import com.pavex.shipment.dto.CreateCounterShipmentRequest
import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.security.requirePavexActor
import com.pavex.shipment.service.CreateShipmentService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** Hub staff booking a walk-in customer's shipment at their hub's counter. */
@RestController
@RequestMapping("/api/v1/admin/shipments/counter")
class CounterShipmentController(
    private val createShipment: CreateShipmentService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('shipments.counter.create')")
    fun create(
        authentication: Authentication,
        @RequestHeader(name = PavexHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateCounterShipmentRequest,
    ): ResponseEntity<ShipmentCreatedResponse> {
        val actor = authentication.requirePavexActor()
        val result = createShipment.createAtCounter(actor.userId, idempotencyKey, request, allHubs = actor.seesAllHubs)
        if (result.replayed) {
            return ResponseEntity.ok()
                .header("Idempotent-Replayed", "true")
                .body(result.shipment)
        }

        return ResponseEntity.created(URI.create("/api/v1/admin/shipments/${result.shipment.trackingCode}"))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(result.shipment)
    }
}
