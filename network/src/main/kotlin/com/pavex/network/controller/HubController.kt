package com.pavex.network.controller

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.hub.CreateHubRequest
import com.pavex.network.dto.hub.HubResponse
import com.pavex.network.dto.hub.UpdateHubRequest
import com.pavex.network.dto.hub.UpdateHubStatusRequest
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.HubType
import com.pavex.network.service.HubService
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
@RequestMapping("/api/v1/admin/network/hubs")
class HubController(private val service: HubService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun list(
        @RequestParam(required = false) status: HubStatus?,
        @RequestParam(required = false) type: HubType?,
        @RequestParam(required = false) provinceCode: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): PageResponse<HubResponse> = service.listAdmin(status, type, provinceCode, search, page, pageSize)

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun get(@PathVariable id: UUID): HubResponse = service.get(id)

    @PostMapping
    @PreAuthorize("hasAuthority('network.manage')")
    fun create(@Valid @RequestBody request: CreateHubRequest, @AuthenticationPrincipal jwt: Jwt): HubResponse =
        service.create(request, UUID.fromString(jwt.subject))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('network.manage')")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: UpdateHubRequest, @AuthenticationPrincipal jwt: Jwt): HubResponse =
        service.update(id, request, UUID.fromString(jwt.subject))

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('network.manage')")
    fun updateStatus(@PathVariable id: UUID, @Valid @RequestBody request: UpdateHubStatusRequest, @AuthenticationPrincipal jwt: Jwt): HubResponse =
        service.updateStatus(id, request, UUID.fromString(jwt.subject))
}
