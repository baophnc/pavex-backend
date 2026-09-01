package com.pavex.operations.controller

import com.pavex.common.api.PageResponse
import com.pavex.operations.dto.AssignWorkforceHubRequest
import com.pavex.operations.dto.CreateWorkforceMemberRequest
import com.pavex.operations.dto.OnboardWorkforceMemberRequest
import com.pavex.operations.dto.SetWorkforceCapacityRequest
import com.pavex.operations.dto.SetWorkforceStatusRequest
import com.pavex.operations.dto.WorkforceAuditResponse
import com.pavex.operations.dto.WorkforceMemberResponse
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.service.WorkforceAccessScopeService
import com.pavex.operations.service.WorkforceOnboardingService
import com.pavex.operations.service.WorkforceService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/operations/workforce")
@PreAuthorize("hasAnyAuthority('operations.workforce.manage','operations.hub.manage')")
class AdminWorkforceController(
    private val service: WorkforceService,
    private val onboarding: WorkforceOnboardingService,
    private val accessScopes: WorkforceAccessScopeService,
) {
    @GetMapping
    fun search(
        authentication: Authentication,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) status: WorkforceMemberStatus?,
        @RequestParam(required = false) kind: WorkforceMemberKind?,
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): PageResponse<WorkforceMemberResponse> =
        service.search(accessScopes.resolve(authentication), search, status, kind, hubId, page, pageSize)

    @GetMapping("/{id}")
    fun get(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): WorkforceMemberResponse = service.get(accessScopes.resolve(authentication), id)

    @PostMapping("/onboard")
    @PreAuthorize("hasAuthority('operations.workforce.manage')")
    @ResponseStatus(HttpStatus.CREATED)
    fun onboard(
        authentication: Authentication,
        @RequestBody request: OnboardWorkforceMemberRequest,
    ): WorkforceMemberResponse = onboarding.onboard(actor(authentication), accessScopes.resolve(authentication), request)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @RequestBody request: CreateWorkforceMemberRequest,
    ): WorkforceMemberResponse = service.create(actor(authentication), accessScopes.resolve(authentication), request)

    @PatchMapping("/{id}/status")
    fun setStatus(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: SetWorkforceStatusRequest,
    ): WorkforceMemberResponse = service.setStatus(actor(authentication), accessScopes.resolve(authentication), id, request)

    @PatchMapping("/{id}/capacity")
    fun setCapacity(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: SetWorkforceCapacityRequest,
    ): WorkforceMemberResponse = service.setCapacity(actor(authentication), accessScopes.resolve(authentication), id, request)

    @PostMapping("/{id}/hubs")
    fun assignHub(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: AssignWorkforceHubRequest,
    ): WorkforceMemberResponse = service.assignHub(actor(authentication), accessScopes.resolve(authentication), id, request)

    @DeleteMapping("/{id}/hubs/{hubId}")
    fun unassignHub(
        authentication: Authentication,
        @PathVariable id: UUID,
        @PathVariable hubId: UUID,
        @RequestParam(required = false) reason: String?,
    ): WorkforceMemberResponse = service.unassignHub(actor(authentication), accessScopes.resolve(authentication), id, hubId, reason)

    @GetMapping("/{id}/audit")
    fun audit(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): List<WorkforceAuditResponse> = service.audit(accessScopes.resolve(authentication), id)

    private fun actor(authentication: Authentication): UUID = UUID.fromString(authentication.name)
}
