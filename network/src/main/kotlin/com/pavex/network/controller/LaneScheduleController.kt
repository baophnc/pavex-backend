package com.pavex.network.controller

import com.pavex.network.dto.lane.CreateLaneScheduleRequest
import com.pavex.network.dto.lane.LaneScheduleResponse
import com.pavex.network.dto.lane.UpdateLaneScheduleRequest
import com.pavex.network.dto.lane.UpdateLaneScheduleStatusRequest
import com.pavex.network.service.LaneScheduleService
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
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/network/lanes/{laneId}/schedules")
class LaneScheduleController(private val service: LaneScheduleService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun list(@PathVariable laneId: UUID): List<LaneScheduleResponse> = service.list(laneId)

    @GetMapping("/{scheduleId}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun get(
        @PathVariable laneId: UUID,
        @PathVariable scheduleId: UUID,
    ): LaneScheduleResponse = service.get(laneId, scheduleId)

    @PostMapping
    @PreAuthorize("hasAuthority('network.manage')")
    fun create(
        @PathVariable laneId: UUID,
        @Valid @RequestBody request: CreateLaneScheduleRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): LaneScheduleResponse = service.create(laneId, request, UUID.fromString(jwt.subject))

    @PutMapping("/{scheduleId}")
    @PreAuthorize("hasAuthority('network.manage')")
    fun update(
        @PathVariable laneId: UUID,
        @PathVariable scheduleId: UUID,
        @Valid @RequestBody request: UpdateLaneScheduleRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): LaneScheduleResponse = service.update(laneId, scheduleId, request, UUID.fromString(jwt.subject))

    @PatchMapping("/{scheduleId}/status")
    @PreAuthorize("hasAuthority('network.manage')")
    fun updateStatus(
        @PathVariable laneId: UUID,
        @PathVariable scheduleId: UUID,
        @Valid @RequestBody request: UpdateLaneScheduleStatusRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): LaneScheduleResponse = service.updateStatus(laneId, scheduleId, request, UUID.fromString(jwt.subject))
}
