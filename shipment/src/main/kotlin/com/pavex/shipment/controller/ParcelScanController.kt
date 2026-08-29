package com.pavex.shipment.controller

import com.pavex.shipment.dto.RecordParcelScanRequest
import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.service.RecordParcelScanService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/parcel-scans")
class ParcelScanController(
    private val scans: RecordParcelScanService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('scans.create')")
    fun record(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: RecordParcelScanRequest,
    ): ResponseEntity<ShipmentDetailResponse> = ResponseEntity.ok(
        scans.record(UUID.fromString(jwt.subject), request),
    )
}
