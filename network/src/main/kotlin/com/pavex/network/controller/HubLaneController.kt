package com.pavex.network.controller

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.lane.CreateHubLaneRequest
import com.pavex.network.dto.lane.HubLaneResponse
import com.pavex.network.dto.lane.UpdateHubLaneRequest
import com.pavex.network.dto.lane.UpdateHubLaneStatusRequest
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.TransportMode
import com.pavex.network.service.HubLaneService
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
@RequestMapping("/api/v1/admin/network/lanes")
class HubLaneController(private val service: HubLaneService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun list(
        @RequestParam(required = false) status: HubLaneStatus?,
        @RequestParam(required = false) transportMode: TransportMode?,
        @RequestParam(required = false) fromHubId: UUID?,
        @RequestParam(required = false) toHubId: UUID?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): PageResponse<HubLaneResponse> =
        service.list(status, transportMode, fromHubId, toHubId, search, page, pageSize)

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun get(@PathVariable id: UUID): HubLaneResponse = service.get(id)

    @PostMapping
    @PreAuthorize("hasAuthority('network.manage')")
    fun create(
        @Valid @RequestBody request: CreateHubLaneRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): HubLaneResponse = service.create(request, UUID.fromString(jwt.subject))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('network.manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateHubLaneRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): HubLaneResponse = service.update(id, request, UUID.fromString(jwt.subject))

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('network.manage')")
    fun updateStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateHubLaneStatusRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): HubLaneResponse = service.updateStatus(id, request, UUID.fromString(jwt.subject))
}
