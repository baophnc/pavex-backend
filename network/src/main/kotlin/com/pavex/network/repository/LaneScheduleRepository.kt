package com.pavex.network.repository

import com.pavex.network.entity.LaneSchedule
import com.pavex.network.entity.LaneScheduleStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LaneScheduleRepository : JpaRepository<LaneSchedule, UUID> {
    fun findAllByLaneIdOrderByDepartureTimeAsc(laneId: UUID): List<LaneSchedule>
    fun findAllByLaneIdAndStatusOrderByDepartureTimeAsc(
        laneId: UUID,
        status: LaneScheduleStatus,
    ): List<LaneSchedule>

    fun findAllByLaneIdInAndStatus(
        laneIds: Collection<UUID>,
        status: LaneScheduleStatus,
    ): List<LaneSchedule>

    fun findByIdAndLaneId(id: UUID, laneId: UUID): LaneSchedule?
}
