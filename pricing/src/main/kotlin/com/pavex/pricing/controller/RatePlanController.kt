package com.pavex.pricing.controller

import com.pavex.pricing.dto.CreateRatePlanRequest
import com.pavex.pricing.dto.CreateRatePlanRevisionRequest
import com.pavex.pricing.dto.RatePlanLifecycleRequest
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
    ): RatePlanResponse = service.create(request, actor(jwt))

    @PostMapping("/{id}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun createRevision(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateRatePlanRevisionRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RatePlanResponse = service.createRevision(id, request, actor(jwt))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRatePlanRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RatePlanResponse = service.update(id, request, actor(jwt))

    @PutMapping("/{id}/rules/{serviceLevel}/{pricingZone}")
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun upsertRule(
        @PathVariable id: UUID,
        @PathVariable serviceLevel: ServiceLevel,
        @PathVariable pricingZone: PricingZone,
        @Valid @RequestBody request: UpsertRateRuleRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): RatePlanResponse = service.upsertRule(id, serviceLevel, pricingZone, request, actor(jwt))

    @DeleteMapping("/{id}/rules/{serviceLevel}/{pricingZone}")
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun deleteRule(
        @PathVariable id: UUID,
        @PathVariable serviceLevel: ServiceLevel,
        @PathVariable pricingZone: PricingZone,
        @AuthenticationPrincipal jwt: Jwt,
    ): RatePlanResponse = service.deleteRule(id, serviceLevel, pricingZone, actor(jwt))

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun activate(
        @PathVariable id: UUID,
        @Valid @RequestBody(required = false) request: RatePlanLifecycleRequest?,
        @AuthenticationPrincipal jwt: Jwt,
    ): RatePlanResponse = service.activate(id, actor(jwt), request?.reason)

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun retire(
        @PathVariable id: UUID,
        @Valid @RequestBody(required = false) request: RatePlanLifecycleRequest?,
        @AuthenticationPrincipal jwt: Jwt,
    ): RatePlanResponse = service.retire(id, actor(jwt), request?.reason)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing.manage')")
    fun deleteDraft(
        @PathVariable id: UUID,
        @RequestParam(required = false) reason: String?,
        @AuthenticationPrincipal jwt: Jwt,
    ) = service.deleteDraft(id, actor(jwt), reason)

    private fun actor(jwt: Jwt): UUID = try {
        UUID.fromString(jwt.subject)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("User JWT subject must be a UUID.")
    }
}
