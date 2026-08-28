package com.pavex.network.controller

import com.pavex.network.dto.region.CreateRegionRequest
import com.pavex.network.dto.region.RegionResponse
import com.pavex.network.dto.region.UpdateRegionRequest
import com.pavex.network.dto.region.UpdateRegionStatusRequest
import com.pavex.network.service.NetworkRegionService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/network/regions")
class NetworkRegionController(private val service: NetworkRegionService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun list(@RequestParam(defaultValue = "false") activeOnly: Boolean): List<RegionResponse> = service.list(activeOnly)

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun get(@PathVariable id: UUID): RegionResponse = service.get(id)

    @PostMapping
    @PreAuthorize("hasAuthority('network.manage')")
    fun create(@Valid @RequestBody request: CreateRegionRequest, @AuthenticationPrincipal jwt: Jwt): RegionResponse =
        service.create(request, UUID.fromString(jwt.subject))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('network.manage')")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: UpdateRegionRequest, @AuthenticationPrincipal jwt: Jwt): RegionResponse =
        service.update(id, request, UUID.fromString(jwt.subject))

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('network.manage')")
    fun updateStatus(@PathVariable id: UUID, @Valid @RequestBody request: UpdateRegionStatusRequest, @AuthenticationPrincipal jwt: Jwt): RegionResponse =
        service.updateStatus(id, request, UUID.fromString(jwt.subject))
}
