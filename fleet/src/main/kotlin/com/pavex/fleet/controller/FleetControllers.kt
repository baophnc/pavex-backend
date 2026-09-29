package com.pavex.fleet.controller

import com.pavex.common.security.PavexActor
import com.pavex.fleet.dto.AcceptConsentRequest
import com.pavex.fleet.dto.ConsentResponse
import com.pavex.fleet.dto.DriverTripResponse
import com.pavex.fleet.dto.DriverTripStatus
import com.pavex.fleet.dto.RecordLocationsRequest
import com.pavex.fleet.dto.RecordLocationsResponse
import com.pavex.fleet.dto.StartTripRequest
import com.pavex.fleet.dto.TripSummaryResponse
import com.pavex.fleet.dto.TripTrackResponse
import com.pavex.fleet.service.DriverTripService
import com.pavex.fleet.service.TripWatchService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The line-haul driver's own trips, from the field app. */
@RestController
@RequestMapping("/api/v1/fleet/me")
@PreAuthorize("hasAuthority('fleet.trips.report-own')")
class DriverTripController(
    private val driverTrips: DriverTripService,
) {
    @GetMapping("/consent")
    fun consent(authentication: JwtAuthenticationToken): ConsentResponse =
        driverTrips.consent(authentication.actor().userId)

    @PutMapping("/consent")
    fun acceptConsent(authentication: JwtAuthenticationToken, @Valid @RequestBody request: AcceptConsentRequest): ConsentResponse =
        driverTrips.acceptConsent(authentication.actor().userId, request)

    @GetMapping("/trips")
    fun trips(authentication: JwtAuthenticationToken): List<DriverTripResponse> =
        driverTrips.myTrips(authentication.actor().userId)

    @PostMapping("/trips/start")
    fun start(authentication: JwtAuthenticationToken, @Valid @RequestBody request: StartTripRequest): DriverTripResponse =
        driverTrips.start(authentication.actor().userId, authentication.email(), request)

    /** Called after a successful "load onto truck" scan; 204 when no departure is being loaded. */
    @PostMapping("/trips/start-from-scan")
    fun startFromScan(authentication: JwtAuthenticationToken): ResponseEntity<DriverTripResponse> =
        driverTrips.startFromScan(authentication.actor().userId, authentication.email())
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.noContent().build()

    @PostMapping("/trips/{tripId}/arrive")
    fun arrive(authentication: JwtAuthenticationToken, @PathVariable tripId: UUID): DriverTripResponse =
        driverTrips.arrive(authentication.actor().userId, tripId)

    @PostMapping("/trips/{tripId}/locations")
    fun locations(
        authentication: JwtAuthenticationToken,
        @PathVariable tripId: UUID,
        @Valid @RequestBody request: RecordLocationsRequest,
    ): RecordLocationsResponse = driverTrips.recordLocations(authentication.actor().userId, tripId, request.points)
}

/** Hub managers and head office watching trips on the map. */
@RestController
@RequestMapping("/api/v1/fleet/trips")
@PreAuthorize("hasAuthority('fleet.trips.read-hub')")
class TripWatchController(
    private val watch: TripWatchService,
) {
    @GetMapping
    fun list(
        authentication: JwtAuthenticationToken,
        @RequestParam(required = false) status: DriverTripStatus?,
        @RequestParam(required = false) hubId: UUID?,
    ): List<TripSummaryResponse> = watch.list(authentication.actor(), status, hubId)

    @GetMapping("/{tripId}")
    fun detail(authentication: JwtAuthenticationToken, @PathVariable tripId: UUID): TripSummaryResponse =
        watch.detail(authentication.actor(), tripId)

    @GetMapping("/{tripId}/track")
    fun track(authentication: JwtAuthenticationToken, @PathVariable tripId: UUID): TripTrackResponse =
        watch.track(authentication.actor(), tripId)
}

private fun JwtAuthenticationToken.actor(): PavexActor =
    PavexActor.parse(name, authorities.mapNotNull { it.authority })
        ?: throw AccessDeniedException("Authenticated user id is invalid")

private fun JwtAuthenticationToken.email(): String? = token.getClaimAsString("email")
