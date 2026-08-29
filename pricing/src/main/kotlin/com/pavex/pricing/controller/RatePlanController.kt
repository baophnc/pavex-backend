package com.pavex.pricing.controller

import com.pavex.pricing.dto.CreateRatePlanRequest
import com.pavex.pricing.dto.RatePlanResponse
import com.pavex.pricing.dto.UpdateRatePlanRequest
import com.pavex.pricing.dto.UpsertRateRuleRequest
import com.pavex.pricing.entity.PricingZone
import com.pavex.pricing.entity.RatePlanStatus
import com.pavex.pricing.entity.ServiceLevel
import com.pavex.pricing.service.RatePlanService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/pricing/rate-plans")
class RatePlanController(
    private val service: RatePlanService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('pricing.read','pricing.manage')")
    fun list(@RequestParam(required = false) status: RatePlanStatus?): List<RatePlanResponse> = service.list(status)

    @GetMapping("/current")
    @PreAuthorize("hasAnyAuthority('pricing.read','pricing.manage')")
    fun current(@RequestParam(required = false) at: Instant?): RatePlanResponse = service.current(at ?: Instant.now())

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('pricing.read','pricing.manage')")
    fun get(@PathVariable id: UUID): RatePlanResponse = service.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun create(
        @Valid @RequestBody request: CreateRatePlanRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RatePlanResponse = service.create(request, UUID.fromString(jwt.subject))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRatePlanRequest,
    ): RatePlanResponse = service.update(id, request)

    @PutMapping("/{id}/rules/{serviceLevel}/{pricingZone}")
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun upsertRule(
        @PathVariable id: UUID,
        @PathVariable serviceLevel: ServiceLevel,
        @PathVariable pricingZone: PricingZone,
        @Valid @RequestBody request: UpsertRateRuleRequest,
    ): RatePlanResponse = service.upsertRule(id, serviceLevel, pricingZone, request)

    @DeleteMapping("/{id}/rules/{serviceLevel}/{pricingZone}")
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun deleteRule(
        @PathVariable id: UUID,
        @PathVariable serviceLevel: ServiceLevel,
        @PathVariable pricingZone: PricingZone,
    ): RatePlanResponse = service.deleteRule(id, serviceLevel, pricingZone)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun deleteDraft(@PathVariable id: UUID) = service.deleteDraft(id)
}
