package com.pavex.operations.controller

import com.pavex.operations.dto.CompleteOwnAssignmentRequest
import com.pavex.operations.dto.MyAvailabilityResponse
import com.pavex.operations.dto.OperationalAssignmentResponse
import com.pavex.operations.dto.RejectOwnAssignmentRequest
import com.pavex.operations.dto.SetMyAvailabilityRequest
import com.pavex.operations.service.OperationalAssignmentService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/operations")
class OperationsAssignmentController(
    private val service: OperationalAssignmentService,
) {
    @GetMapping("/assignments/mine")
    @PreAuthorize("hasAuthority('assignments.read-own')")
    fun mine(authentication: Authentication): List<OperationalAssignmentResponse> = service.mine(actor(authentication))

    @PutMapping("/availability")
    @PreAuthorize("hasAuthority('assignments.execute-own')")
    fun availability(
        authentication: Authentication,
        @RequestBody request: SetMyAvailabilityRequest,
    ): MyAvailabilityResponse = service.setMyAvailability(actor(authentication), request.status)

    @PostMapping("/assignments/{id}/accept")
    @PreAuthorize("hasAuthority('assignments.accept-own')")
    fun accept(authentication: Authentication, @PathVariable id: UUID): OperationalAssignmentResponse =
        service.accept(actor(authentication), id)

    @PostMapping("/assignments/{id}/reject")
    @PreAuthorize("hasAuthority('assignments.execute-own')")
    fun reject(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: RejectOwnAssignmentRequest,
    ): OperationalAssignmentResponse = service.reject(actor(authentication), id, request.reason)

    @PostMapping("/assignments/{id}/start")
    @PreAuthorize("hasAuthority('assignments.execute-own')")
    fun start(authentication: Authentication, @PathVariable id: UUID): OperationalAssignmentResponse =
        service.start(actor(authentication), id)

    @PostMapping("/assignments/{id}/complete")
    @PreAuthorize("hasAuthority('assignments.execute-own')")
    fun complete(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: CompleteOwnAssignmentRequest,
    ): OperationalAssignmentResponse = service.complete(actor(authentication), id, request.note)

    private fun actor(authentication: Authentication): UUID = UUID.fromString(authentication.name)
}
