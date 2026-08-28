package com.pavex.network.controller

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.servicearea.CreateServiceAreaRequest
import com.pavex.network.dto.servicearea.ServiceAreaResponse
import com.pavex.network.dto.servicearea.UpdateServiceAreaRequest
import com.pavex.network.dto.servicearea.UpdateServiceAreaStatusRequest
import com.pavex.network.entity.ServiceAreaStatus
import com.pavex.network.service.ServiceAreaService
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
@RequestMapping("/api/v1/admin/network/service-areas")
class ServiceAreaController(private val service: ServiceAreaService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun list(
        @RequestParam(required = false) status: ServiceAreaStatus?,
        @RequestParam(required = false) regionId: UUID?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): PageResponse<ServiceAreaResponse> = service.list(status, regionId, search, page, pageSize)

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun get(@PathVariable id: UUID): ServiceAreaResponse = service.get(id)

    @PostMapping
    @PreAuthorize("hasAuthority('network.manage')")
    fun create(@Valid @RequestBody request: CreateServiceAreaRequest, @AuthenticationPrincipal jwt: Jwt): ServiceAreaResponse =
        service.create(request, UUID.fromString(jwt.subject))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('network.manage')")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: UpdateServiceAreaRequest, @AuthenticationPrincipal jwt: Jwt): ServiceAreaResponse =
        service.update(id, request, UUID.fromString(jwt.subject))

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('network.manage')")
    fun updateStatus(@PathVariable id: UUID, @Valid @RequestBody request: UpdateServiceAreaStatusRequest, @AuthenticationPrincipal jwt: Jwt): ServiceAreaResponse =
        service.updateStatus(id, request, UUID.fromString(jwt.subject))
}
