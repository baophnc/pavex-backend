package com.pavex.shipment.controller

import com.pavex.common.http.PavexHeaders
import com.pavex.shipment.dto.BulkCreateShipmentsRequest
import com.pavex.shipment.dto.BulkCreateShipmentsResponse
import com.pavex.shipment.dto.CancelShipmentRequest
import com.pavex.shipment.dto.CreateShipmentRequest
import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.dto.ShipmentLabelsResponse
import com.pavex.shipment.dto.ShipmentPageResponse
import com.pavex.shipment.dto.UpdateShipmentRequest
import com.pavex.shipment.service.BulkCreateShipmentService
import com.pavex.shipment.service.CancelShipmentService
import com.pavex.shipment.service.CreateShipmentService
import com.pavex.shipment.service.ShipmentQueryService
import com.pavex.shipment.service.ShopStatsResponse
import com.pavex.shipment.service.ShopStatsService
import com.pavex.shipment.service.UpdateShipmentService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/shipments")
class ShipmentController(
    private val bulkCreate: BulkCreateShipmentService,
    private val createShipment: CreateShipmentService,
    private val queries: ShipmentQueryService,
    private val cancellations: CancelShipmentService,
    private val updates: UpdateShipmentService,
    private val shopStats: ShopStatsService,
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

    /** A slice (up to 50 rows) of a shop's spreadsheet upload; the result says per row what was created. */
    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('shipments.create')")
    fun createBulk(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: BulkCreateShipmentsRequest,
    ): BulkCreateShipmentsResponse = bulkCreate.create(UUID.fromString(jwt.subject), request)

    @GetMapping
    @PreAuthorize("hasAuthority('shipments.read-own')")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) q: String?,
    ): ResponseEntity<ShipmentPageResponse> = ResponseEntity.ok(
        queries.listOwned(UUID.fromString(jwt.subject), status, cursor, limit, q),
    )

    /** The shop's own daily figures and open orders, for its dashboard. */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('shipments.read-own')")
    fun stats(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ): ShopStatsResponse = shopStats.stats(UUID.fromString(jwt.subject), from, to)

    @GetMapping("/{trackingCode}")
    @PreAuthorize("hasAuthority('shipments.read-own')")
    fun get(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable trackingCode: String,
    ): ResponseEntity<ShipmentDetailResponse> = ResponseEntity.ok(
        queries.getOwned(UUID.fromString(jwt.subject), trackingCode),
    )

    @GetMapping("/{trackingCode}/labels")
    @PreAuthorize("hasAuthority('shipments.read-own')")
    fun labels(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable trackingCode: String,
    ): ResponseEntity<ShipmentLabelsResponse> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(queries.labelsOwned(UUID.fromString(jwt.subject), trackingCode))

    /** The merchant edits its own shipment before pickup; price and route stay as they are. */
    @PatchMapping("/{trackingCode}")
    @PreAuthorize("hasAuthority('shipments.create')")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable trackingCode: String,
        @Valid @RequestBody request: UpdateShipmentRequest,
    ): ResponseEntity<ShipmentDetailResponse> = ResponseEntity.ok(
        updates.updateOwn(UUID.fromString(jwt.subject), trackingCode, request),
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
