package com.pavex.network.controller

import com.pavex.network.dto.capacity.CapacityReservationResponse
import com.pavex.network.dto.capacity.LaneCapacityResponse
import com.pavex.network.dto.capacity.ReserveLaneCapacityRequest
import com.pavex.network.service.NetworkCapacityService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/network")
class NetworkCapacityController(private val service: NetworkCapacityService) {
    @GetMapping("/lanes/{laneId}/capacity")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun getLaneCapacity(
        @PathVariable laneId: UUID,
        @RequestParam serviceDate: LocalDate,
    ): LaneCapacityResponse = service.getLaneCapacity(laneId, serviceDate)

    @GetMapping("/capacity/reservations/{shipmentId}/revisions/{routeRevision}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun getReservation(
        @PathVariable shipmentId: UUID,
        @PathVariable routeRevision: Int,
    ): CapacityReservationResponse = service.getReservation(shipmentId, routeRevision)

    @PostMapping("/capacity/reservations")
    @PreAuthorize("hasAuthority('network.manage')")
    fun reserve(
        @Valid @RequestBody request: ReserveLaneCapacityRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CapacityReservationResponse> {
        val response = service.reserve(request, UUID.fromString(jwt.subject))
        return ResponseEntity.created(
            URI.create(
                "/api/v1/admin/network/capacity/reservations/${request.shipmentId}/revisions/${request.routeRevision}",
            ),
        ).body(response)
    }

    @PostMapping("/capacity/reservations/{shipmentId}/revisions/{routeRevision}/release")
    @PreAuthorize("hasAuthority('network.manage')")
    fun release(
        @PathVariable shipmentId: UUID,
        @PathVariable routeRevision: Int,
        @RequestParam(required = false) reason: String?,
        @AuthenticationPrincipal jwt: Jwt,
    ): CapacityReservationResponse =
        service.release(shipmentId, routeRevision, UUID.fromString(jwt.subject), reason)

    @PostMapping("/capacity/reservations/{shipmentId}/revisions/{routeRevision}/legs/{legSequence}/consume")
    @PreAuthorize("hasAuthority('network.manage')")
    fun consumeLeg(
        @PathVariable shipmentId: UUID,
        @PathVariable routeRevision: Int,
        @PathVariable legSequence: Int,
        @AuthenticationPrincipal jwt: Jwt,
    ): CapacityReservationResponse =
        service.consumeLeg(shipmentId, routeRevision, legSequence, UUID.fromString(jwt.subject))
}
