package com.pavex.operations.controller

import com.pavex.operations.dto.AssignOperationalWorkRequest
import com.pavex.operations.dto.CancelOperationalAssignmentRequest
import com.pavex.operations.dto.OperationalAssignmentResponse
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.service.AssignmentAccessScopeService
import com.pavex.operations.service.OperationalAssignmentService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/operations/assignments")
@PreAuthorize("hasAnyAuthority('operations.assignments.manage','operations.hub.manage')")
class AdminOperationalAssignmentController(
    private val service: OperationalAssignmentService,
    private val accessScopes: AssignmentAccessScopeService,
) {
    @GetMapping
    fun search(
        authentication: Authentication,
        @RequestParam(required = false) shipmentId: UUID?,
        @RequestParam(required = false) hubId: UUID?,
        @RequestParam(required = false) status: OperationalAssignmentStatus?,
    ): List<OperationalAssignmentResponse> =
        service.search(accessScopes.resolve(authentication), shipmentId, hubId, status)

    @PostMapping("/{id}/assign")
    fun assign(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: AssignOperationalWorkRequest,
    ): OperationalAssignmentResponse = service.assign(
        actor(authentication),
        accessScopes.resolve(authentication),
        id,
        request.workforceMemberId,
    )

    @PostMapping("/{id}/cancel")
    fun cancel(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: CancelOperationalAssignmentRequest,
    ): OperationalAssignmentResponse = service.cancel(
        actor(authentication),
        accessScopes.resolve(authentication),
        id,
        request.reason,
    )

    private fun actor(authentication: Authentication): UUID = UUID.fromString(authentication.name)
}
