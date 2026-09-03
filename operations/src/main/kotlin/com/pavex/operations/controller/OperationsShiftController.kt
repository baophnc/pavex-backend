package com.pavex.operations.controller

import com.pavex.operations.dto.MyWorkShiftResponse
import com.pavex.operations.dto.WorkforceShiftAssignmentResponse
import com.pavex.operations.security.requirePavexActor
import com.pavex.operations.service.WorkShiftService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/operations/shifts")
class OperationsShiftController(
    private val service: WorkShiftService,
) {
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('assignments.read-own')")
    fun mine(
        authentication: Authentication,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
    ): List<MyWorkShiftResponse> = service.mine(actor(authentication), from, to)

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAuthority('assignments.execute-own')")
    fun checkIn(authentication: Authentication, @PathVariable id: UUID): WorkforceShiftAssignmentResponse =
        service.checkIn(actor(authentication), id)

    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasAuthority('assignments.execute-own')")
    fun checkOut(authentication: Authentication, @PathVariable id: UUID): WorkforceShiftAssignmentResponse =
        service.checkOut(actor(authentication), id)

    private fun actor(authentication: Authentication): UUID = authentication.requirePavexActor().userId
}
