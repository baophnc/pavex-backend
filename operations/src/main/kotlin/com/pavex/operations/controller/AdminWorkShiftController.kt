package com.pavex.operations.controller

import com.pavex.common.api.PageResponse
import com.pavex.operations.dto.AssignWorkforceShiftRequest
import com.pavex.operations.dto.CancelWorkShiftRequest
import com.pavex.operations.dto.CreateWorkShiftRequest
import com.pavex.operations.dto.MarkShiftAbsentRequest
import com.pavex.operations.dto.UpdateWorkShiftRequest
import com.pavex.operations.dto.WorkShiftDetailResponse
import com.pavex.operations.dto.WorkShiftResponse
import com.pavex.operations.dto.WorkforceShiftAssignmentResponse
import com.pavex.operations.entity.WorkShiftStatus
import com.pavex.operations.service.WorkShiftService
import com.pavex.operations.service.WorkforceAccessScopeService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
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
@RequestMapping("/api/v1/admin/operations/shifts")
@PreAuthorize("hasAnyAuthority('operations.workforce.manage','operations.hub.manage')")
class AdminWorkShiftController(
    private val service: WorkShiftService,
    private val accessScopes: WorkforceAccessScopeService,
) {
    @GetMapping
    fun search(
        authentication: Authentication,
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(required = false) status: WorkShiftStatus?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): PageResponse<WorkShiftResponse> =
        service.search(accessScopes.resolve(authentication), hubId, status, from, to, page, pageSize)

    @GetMapping("/{id}")
    fun get(authentication: Authentication, @PathVariable id: UUID): WorkShiftDetailResponse =
        service.get(accessScopes.resolve(authentication), id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(authentication: Authentication, @RequestBody request: CreateWorkShiftRequest): WorkShiftResponse =
        service.create(actor(authentication), accessScopes.resolve(authentication), request)

    @PutMapping("/{id}")
    fun update(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: UpdateWorkShiftRequest,
    ): WorkShiftResponse = service.update(actor(authentication), accessScopes.resolve(authentication), id, request)

    @PostMapping("/{id}/cancel")
    fun cancel(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: CancelWorkShiftRequest,
    ): WorkShiftResponse = service.cancel(actor(authentication), accessScopes.resolve(authentication), id, request.reason)

    @PostMapping("/{id}/members")
    fun assignMember(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: AssignWorkforceShiftRequest,
    ): WorkforceShiftAssignmentResponse = service.assignMember(
        actor(authentication),
        accessScopes.resolve(authentication),
        id,
        request.workforceMemberId,
    )

    @DeleteMapping("/{id}/members/{memberId}")
    fun removeMember(
        authentication: Authentication,
        @PathVariable id: UUID,
        @PathVariable memberId: UUID,
        @RequestParam(required = false) reason: String?,
    ): WorkforceShiftAssignmentResponse =
        service.removeMember(actor(authentication), accessScopes.resolve(authentication), id, memberId, reason)

    @PostMapping("/{id}/members/{memberId}/absent")
    fun markAbsent(
        authentication: Authentication,
        @PathVariable id: UUID,
        @PathVariable memberId: UUID,
        @RequestBody request: MarkShiftAbsentRequest,
    ): WorkforceShiftAssignmentResponse =
        service.markAbsent(actor(authentication), accessScopes.resolve(authentication), id, memberId, request.note)

    private fun actor(authentication: Authentication): UUID = UUID.fromString(authentication.name)
}
