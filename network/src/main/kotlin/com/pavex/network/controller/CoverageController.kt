package com.pavex.network.controller

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.coverage.CoverageResponse
import com.pavex.network.dto.coverage.CreateCoverageRequest
import com.pavex.network.dto.coverage.UpdateCoverageRequest
import com.pavex.network.dto.coverage.UpdateCoverageStatusRequest
import com.pavex.network.service.CoverageService
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
@RequestMapping("/api/v1/admin/network/coverages")
class CoverageController(private val service: CoverageService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun list(
        @RequestParam(required = false) serviceAreaId: UUID?,
        @RequestParam(required = false) provinceCode: String?,
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): PageResponse<CoverageResponse> = service.list(serviceAreaId, provinceCode, activeOnly, page, pageSize)

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun get(@PathVariable id: UUID): CoverageResponse = service.get(id)

    @PostMapping
    @PreAuthorize("hasAuthority('network.manage')")
    fun create(@Valid @RequestBody request: CreateCoverageRequest, @AuthenticationPrincipal jwt: Jwt): CoverageResponse =
        service.create(request, UUID.fromString(jwt.subject))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('network.manage')")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: UpdateCoverageRequest, @AuthenticationPrincipal jwt: Jwt): CoverageResponse =
        service.update(id, request, UUID.fromString(jwt.subject))

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('network.manage')")
    fun updateStatus(@PathVariable id: UUID, @Valid @RequestBody request: UpdateCoverageStatusRequest, @AuthenticationPrincipal jwt: Jwt): CoverageResponse =
        service.updateStatus(id, request, UUID.fromString(jwt.subject))
}
