package com.pavex.shipment.controller

import com.pavex.common.http.PavexHeaders
import com.pavex.shipment.dto.CancelShipmentRequest
import com.pavex.shipment.dto.CreateShipmentRequest
import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.dto.ShipmentPageResponse
import com.pavex.shipment.service.CancelShipmentService
import com.pavex.shipment.service.CreateShipmentService
import com.pavex.shipment.service.ShipmentQueryService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/shipments")
class ShipmentController(
    private val createShipment: CreateShipmentService,
    private val queries: ShipmentQueryService,
    private val cancellations: CancelShipmentService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('shipments.create')")
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader(name = PavexHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateShipmentRequest,
    ): ResponseEntity<ShipmentCreatedResponse> {
        val result = createShipment.create(UUID.fromString(jwt.subject), idempotencyKey, request)
        if (result.replayed) {
            return ResponseEntity.ok()
                .header("Idempotent-Replayed", "true")
                .body(result.shipment)
        }

        return ResponseEntity.created(URI.create("/api/v1/shipments/${result.shipment.trackingCode}"))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(result.shipment)
    }

    @GetMapping
    @PreAuthorize("hasAuthority('shipments.read')")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<ShipmentPageResponse> = ResponseEntity.ok(
        queries.listOwned(UUID.fromString(jwt.subject), status, cursor, limit),
    )

    @GetMapping("/{trackingCode}")
    @PreAuthorize("hasAuthority('shipments.read')")
    fun get(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable trackingCode: String,
    ): ResponseEntity<ShipmentDetailResponse> = ResponseEntity.ok(
        queries.getOwned(UUID.fromString(jwt.subject), trackingCode),
    )

    @PostMapping("/{trackingCode}/cancel")
    @PreAuthorize("hasAuthority('shipments.cancel-own')")
    fun cancel(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable trackingCode: String,
        @Valid @RequestBody request: CancelShipmentRequest,
    ): ResponseEntity<ShipmentDetailResponse> = ResponseEntity.ok(
        cancellations.cancel(UUID.fromString(jwt.subject), trackingCode, request.reason),
    )
}
