package com.pavex.shipment.controller

import com.pavex.common.http.PavexHeaders
import com.pavex.shipment.dto.CreateCounterShipmentRequest
import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.service.CreateShipmentService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

/** Hub staff booking a walk-in customer's shipment at their hub's counter. */
@RestController
@RequestMapping("/api/v1/admin/shipments/counter")
class CounterShipmentController(
    private val createShipment: CreateShipmentService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('shipments.counter.create')")
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader(name = PavexHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateCounterShipmentRequest,
    ): ResponseEntity<ShipmentCreatedResponse> {
        val result = createShipment.createAtCounter(UUID.fromString(jwt.subject), idempotencyKey, request)
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
