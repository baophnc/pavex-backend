package com.pavex.network.service

import com.pavex.network.dto.lane.CreateLaneScheduleRequest
import com.pavex.network.dto.lane.LaneScheduleResponse
import com.pavex.network.dto.lane.UpdateLaneScheduleRequest
import com.pavex.network.dto.lane.UpdateLaneScheduleStatusRequest
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.LaneSchedule
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.LaneScheduleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class LaneScheduleService(
    private val lanes: HubLaneRepository,
    private val schedules: LaneScheduleRepository,
    private val audit: NetworkAuditService,
) {
    @Transactional(readOnly = true)
    fun list(laneId: UUID): List<LaneScheduleResponse> {
        findLane(laneId)
        return schedules.findAllByLaneIdOrderByDepartureTimeAsc(laneId).map(LaneScheduleResponse::from)
    }

    @Transactional(readOnly = true)
    fun get(laneId: UUID, scheduleId: UUID): LaneScheduleResponse =
        LaneScheduleResponse.from(findSchedule(laneId, scheduleId))

    @Transactional
    fun create(laneId: UUID, request: CreateLaneScheduleRequest, actorUserId: UUID): LaneScheduleResponse {
        findLane(laneId)
        validateRange(request.daysOfWeek, request.effectiveFrom, request.effectiveTo)
        val schedule = LaneSchedule.create(
            laneId = laneId,
            departureTime = request.departureTime,
            daysOfWeek = request.daysOfWeek,
            effectiveFrom = request.effectiveFrom,
            effectiveTo = request.effectiveTo,
            active = request.active,
        )
        if (schedule.status == LaneScheduleStatus.ACTIVE) ensureNoOverlap(schedule, null)
        val saved = schedules.save(schedule)
        val response = LaneScheduleResponse.from(saved)
        audit.record(NetworkAuditEntityType.LANE_SCHEDULE, saved.id, NetworkAuditAction.CREATED, actorUserId, after = response)
        return response
    }

    @Transactional
    fun update(
        laneId: UUID,
        scheduleId: UUID,
        request: UpdateLaneScheduleRequest,
        actorUserId: UUID,
    ): LaneScheduleResponse {
        val lane = findLane(laneId)
        val schedule = findSchedule(laneId, scheduleId)
        val before = LaneScheduleResponse.from(schedule)
        validateRange(request.daysOfWeek, request.effectiveFrom, request.effectiveTo)
        schedule.update(request.departureTime, request.daysOfWeek, request.effectiveFrom, request.effectiveTo)
        if (schedule.status == LaneScheduleStatus.ACTIVE) ensureNoOverlap(schedule, scheduleId)
        if (lane.status == HubLaneStatus.ACTIVE) ensureLaneHasUsableSchedule(laneId, schedule)
        val saved = schedules.save(schedule)
        val response = LaneScheduleResponse.from(saved)
        audit.record(NetworkAuditEntityType.LANE_SCHEDULE, scheduleId, NetworkAuditAction.UPDATED, actorUserId, before, response)
        return response
    }

    @Transactional
    fun updateStatus(
        laneId: UUID,
        scheduleId: UUID,
        request: UpdateLaneScheduleStatusRequest,
        actorUserId: UUID,
    ): LaneScheduleResponse {
        val lane = findLane(laneId)
        val schedule = findSchedule(laneId, scheduleId)
        val before = LaneScheduleResponse.from(schedule)

        if (request.active) {
            schedule.setActive(true)
            ensureNoOverlap(schedule, scheduleId)
        } else {
            schedule.setActive(false)
            if (lane.status == HubLaneStatus.ACTIVE) ensureLaneHasUsableSchedule(laneId, schedule)
        }

        val saved = schedules.save(schedule)
        val response = LaneScheduleResponse.from(saved)
        audit.record(
            NetworkAuditEntityType.LANE_SCHEDULE,
            scheduleId,
            if (request.active) NetworkAuditAction.ACTIVATED else NetworkAuditAction.DEACTIVATED,
            actorUserId,
            before,
            response,
            request.reason,
        )
        return response
    }

    private fun ensureNoOverlap(candidate: LaneSchedule, excludedId: UUID?) {
        val conflict = schedules.findAllByLaneIdAndStatusOrderByDepartureTimeAsc(
            candidate.laneId,
            LaneScheduleStatus.ACTIVE,
        ).asSequence()
            .filter { it.id != excludedId }
            .filter { it.departureTime == candidate.departureTime }
            .any { existing -> dateRangesOverlap(existing, candidate) && existing.daysOfWeek().intersect(candidate.daysOfWeek()).isNotEmpty() }

        if (conflict) {
            throw ConflictException(
                "lane_schedule_overlap",
                "Another active schedule already uses this departure time on overlapping operating days.",
            )
        }
    }

    private fun ensureLaneHasUsableSchedule(laneId: UUID, replacement: LaneSchedule) {
        val usable = schedules.findAllByLaneIdAndStatusOrderByDepartureTimeAsc(laneId, LaneScheduleStatus.ACTIVE)
            .asSequence()
            .filter { it.id != replacement.id }
            .plus(sequenceOf(replacement).filter { it.status == LaneScheduleStatus.ACTIVE })
            .any(::canOperateFromToday)
        if (!usable) {
            throw ConflictException(
                "active_lane_requires_schedule",
                "Deactivate the lane before removing its last current or future schedule.",
            )
        }
    }

    private fun validateRange(daysOfWeek: Set<java.time.DayOfWeek>, from: LocalDate, to: LocalDate?) {
        if (daysOfWeek.isEmpty()) {
            throw InvalidNetworkRequestException("schedule_days_required", "Select at least one operating day.")
        }
        if (to != null && to.isBefore(from)) {
            throw InvalidNetworkRequestException("invalid_schedule_range", "Schedule effectiveTo cannot be before effectiveFrom.")
        }
    }

    private fun findLane(id: UUID) = lanes.findById(id).orElseThrow {
        NotFoundException("lane_not_found", "Hub lane was not found.")
    }

    private fun findSchedule(laneId: UUID, scheduleId: UUID): LaneSchedule =
        schedules.findByIdAndLaneId(scheduleId, laneId)
            ?: throw NotFoundException("lane_schedule_not_found", "Lane schedule was not found.")

    companion object {
        fun canOperateFromToday(schedule: LaneSchedule): Boolean {
            val today = LocalDate.now(LaneScheduleCalculator.VIETNAM_ZONE)
            return schedule.status == LaneScheduleStatus.ACTIVE &&
                (schedule.effectiveTo == null || !schedule.effectiveTo!!.isBefore(today)) &&
                schedule.daysOfWeek().isNotEmpty()
        }

        private fun dateRangesOverlap(left: LaneSchedule, right: LaneSchedule): Boolean {
            val leftEnd = left.effectiveTo ?: LocalDate.MAX
            val rightEnd = right.effectiveTo ?: LocalDate.MAX
            return !leftEnd.isBefore(right.effectiveFrom) && !rightEnd.isBefore(left.effectiveFrom)
        }
    }
}
