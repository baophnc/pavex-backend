package com.pavex.partners.controller

import com.pavex.contracts.partners.PartnerLocationResolutionRequest
import com.pavex.contracts.partners.PartnerLocationSnapshot
import com.pavex.partners.service.LocationService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Hidden
@RestController
@RequestMapping("/internal/v1/locations")
class InternalLocationController(
    private val locations: LocationService,
) {
    @PostMapping("/resolve")
    @PreAuthorize("hasAuthority('SCOPE_partners.locations.read')")
    fun resolve(@RequestBody request: PartnerLocationResolutionRequest): PartnerLocationSnapshot =
        locations.resolveForShipment(request.provinceCode, request.wardCode)
}
