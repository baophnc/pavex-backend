package com.pavex.network.controller

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.routing.CreateRouteTemplateRequest
import com.pavex.network.dto.routing.RouteTemplateJourneyResponse
import com.pavex.network.dto.routing.RouteTemplateResponse
import com.pavex.network.dto.routing.UpdateRouteTemplateRequest
import com.pavex.network.dto.routing.UpdateRouteTemplateStatusRequest
import com.pavex.network.entity.RouteTemplateStatus
import com.pavex.network.service.RouteTemplateService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/network/route-templates")
class RouteTemplateController(private val service: RouteTemplateService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun list(
        @RequestParam(required = false) status: RouteTemplateStatus?,
        @RequestParam(required = false) originHubId: UUID?,
        @RequestParam(required = false) destinationHubId: UUID?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): PageResponse<RouteTemplateResponse> =
        service.list(status, originHubId, destinationHubId, search, page, pageSize)

    @GetMapping("/journey")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun journey(
        @RequestParam originHubId: UUID,
        @RequestParam destinationHubId: UUID,
        @RequestParam shipmentWeightKg: BigDecimal,
        @RequestParam(required = false) readyAt: Instant?,
    ): RouteTemplateJourneyResponse = service.lookupJourney(
        originHubId,
        destinationHubId,
        shipmentWeightKg,
        readyAt ?: Instant.now(),
    )

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun get(@PathVariable id: UUID): RouteTemplateResponse = service.get(id)

    @PostMapping
    @PreAuthorize("hasAuthority('network.manage')")
    fun create(
        @Valid @RequestBody request: CreateRouteTemplateRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RouteTemplateResponse = service.create(request, UUID.fromString(jwt.subject))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('network.manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRouteTemplateRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RouteTemplateResponse = service.update(id, request, UUID.fromString(jwt.subject))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('network.manage')")
    fun delete(
        @PathVariable id: UUID,
        @RequestParam(required = false) reason: String?,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        service.delete(id, reason, UUID.fromString(jwt.subject))
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('network.manage')")
    fun updateStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRouteTemplateStatusRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RouteTemplateResponse = service.updateStatus(id, request, UUID.fromString(jwt.subject))
}
