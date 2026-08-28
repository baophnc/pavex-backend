package com.pavex.network.service

import com.pavex.network.dto.lane.CreateLaneScheduleRequest
import com.pavex.network.dto.lane.LaneScheduleResponse
import com.pavex.network.dto.lane.UpdateLaneScheduleRequest
import com.pavex.network.dto.lane.UpdateLaneScheduleStatusRequest
import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.LaneCapacityReservationStatus
import com.pavex.network.entity.LaneSchedule
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.LaneCapacityReservationRepository
import com.pavex.network.repository.LaneScheduleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class LaneScheduleService(
    private val lanes: HubLaneRepository,
    private val schedules: LaneScheduleRepository,
    private val reservations: LaneCapacityReservationRepository,
    private val transactionLocks: NetworkTransactionLockService,
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
        transactionLocks.lock("lane:$laneId")
        val lane = findLane(laneId)
        validateRange(request.daysOfWeek, request.effectiveFrom, request.effectiveTo)
        validateCapacity(lane, request.capacityKg)
        val schedule = LaneSchedule.create(
            laneId = laneId,
            departureTime = request.departureTime,
            daysOfWeek = request.daysOfWeek,
            effectiveFrom = request.effectiveFrom,
            effectiveTo = request.effectiveTo,
            capacityKg = request.capacityKg,
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
        transactionLocks.lock("lane:$laneId")
        transactionLocks.lock("schedule:$scheduleId")
        val lane = findLane(laneId)
        val schedule = findSchedule(laneId, scheduleId)
        val before = LaneScheduleResponse.from(schedule)
        validateRange(request.daysOfWeek, request.effectiveFrom, request.effectiveTo)
        validateCapacity(lane, request.capacityKg)

        val scheduleDefinitionChanged = schedule.departureTime != request.departureTime ||
            schedule.daysOfWeek() != request.daysOfWeek ||
            schedule.effectiveFrom != request.effectiveFrom ||
            schedule.effectiveTo != request.effectiveTo
        if (scheduleDefinitionChanged) ensureNoFutureReservedCapacity(scheduleId)
        ensureCapacityNotBelowAllocated(scheduleId, request.capacityKg)

        schedule.update(
            request.departureTime,
            request.daysOfWeek,
            request.effectiveFrom,
            request.effectiveTo,
            request.capacityKg,
        )
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
        transactionLocks.lock("lane:$laneId")
        transactionLocks.lock("schedule:$scheduleId")
        val lane = findLane(laneId)
        val schedule = findSchedule(laneId, scheduleId)
        val before = LaneScheduleResponse.from(schedule)

        if (request.active) {
            schedule.setActive(true)
            ensureNoOverlap(schedule, scheduleId)
        } else {
            if (schedule.status == LaneScheduleStatus.ACTIVE) ensureNoFutureReservedCapacity(scheduleId)
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

    private fun ensureNoFutureReservedCapacity(scheduleId: UUID) {
        if (reservations.existsByScheduleIdAndStatusAndDepartureAtAfter(
                scheduleId,
                LaneCapacityReservationStatus.RESERVED,
                Instant.now(),
            )
        ) {
            throw ConflictException(
                "schedule_has_reserved_capacity",
                "Release future capacity reservations before changing or deactivating this schedule.",
            )
        }
    }

    private fun ensureCapacityNotBelowAllocated(scheduleId: UUID, capacityKg: BigDecimal) {
        val today = LocalDate.now(LaneScheduleCalculator.VIETNAM_ZONE)
        val allocatedByDate = reservations.findAllByScheduleIdAndStatusIn(
            scheduleId,
            listOf(LaneCapacityReservationStatus.RESERVED, LaneCapacityReservationStatus.CONSUMED),
        ).asSequence()
            .filter { !it.serviceDate.isBefore(today) }
            .groupBy { it.serviceDate }
            .mapValues { (_, items) -> items.fold(BigDecimal.ZERO) { total, item -> total.add(item.reservedWeightKg) } }
        if (allocatedByDate.values.any { it > capacityKg }) {
            throw ConflictException(
                "schedule_capacity_below_allocated",
                "Schedule capacity cannot be reduced below capacity already allocated to a departure.",
            )
        }
    }

    private fun validateCapacity(lane: HubLane, capacityKg: BigDecimal) {
        if (capacityKg.signum() <= 0) {
            throw InvalidNetworkRequestException("invalid_schedule_capacity", "Departure capacity must be positive.")
        }
        if (capacityKg < lane.maxShipmentWeightKg) {
            throw InvalidNetworkRequestException(
                "schedule_capacity_below_lane_max",
                "Departure capacity must carry at least one maximum-weight shipment for the lane.",
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
