package com.pavex.network.dto.lane

import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.LaneSchedule
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.entity.TransportMode
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateHubLaneRequest(
    @field:NotBlank
    @field:Size(max = 50)
    @field:Pattern(regexp = "^[A-Za-z0-9_-]+$")
    val code: String,
    val fromHubId: UUID,
    val toHubId: UUID,
    val transportMode: TransportMode,
    @field:Min(1) @field:Max(43200)
    val estimatedTransitMinutes: Int,
    @field:DecimalMin(value = "0.01")
    val distanceKm: BigDecimal? = null,
    @field:DecimalMin(value = "0.001")
    val maxShipmentWeightKg: BigDecimal,
)

data class UpdateHubLaneRequest(
    val fromHubId: UUID,
    val toHubId: UUID,
    val transportMode: TransportMode,
    @field:Min(1) @field:Max(43200)
    val estimatedTransitMinutes: Int,
    @field:DecimalMin(value = "0.01")
    val distanceKm: BigDecimal? = null,
    @field:DecimalMin(value = "0.001")
    val maxShipmentWeightKg: BigDecimal,
)

data class UpdateHubLaneStatusRequest(
    val active: Boolean,
    @field:Size(max = 500) val reason: String? = null,
)

data class HubLaneResponse(
    val id: UUID,
    val code: String,
    val fromHubId: UUID,
    val fromHubCode: String,
    val fromHubName: String,
    val toHubId: UUID,
    val toHubCode: String,
    val toHubName: String,
    val transportMode: TransportMode,
    val estimatedTransitMinutes: Int,
    val distanceKm: BigDecimal?,
    val maxShipmentWeightKg: BigDecimal,
    val status: HubLaneStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(lane: HubLane, fromHub: Hub, toHub: Hub) = HubLaneResponse(
            id = lane.id,
            code = lane.code,
            fromHubId = lane.fromHubId,
            fromHubCode = fromHub.code,
            fromHubName = fromHub.name,
            toHubId = lane.toHubId,
            toHubCode = toHub.code,
            toHubName = toHub.name,
            transportMode = lane.transportMode,
            estimatedTransitMinutes = lane.estimatedTransitMinutes,
            distanceKm = lane.distanceKm,
            maxShipmentWeightKg = lane.maxShipmentWeightKg,
            status = lane.status,
            createdAt = lane.createdAt,
            updatedAt = lane.updatedAt,
        )
    }
}

data class CreateLaneScheduleRequest(
    val departureTime: LocalTime,
    @field:Size(min = 1, max = 7)
    val daysOfWeek: Set<DayOfWeek>,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate? = null,
    @field:DecimalMin(value = "0.001")
    val capacityKg: BigDecimal,
    val active: Boolean = true,
)

data class UpdateLaneScheduleRequest(
    val departureTime: LocalTime,
    @field:Size(min = 1, max = 7)
    val daysOfWeek: Set<DayOfWeek>,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate? = null,
    @field:DecimalMin(value = "0.001")
    val capacityKg: BigDecimal,
)

data class UpdateLaneScheduleStatusRequest(
    val active: Boolean,
    @field:Size(max = 500) val reason: String? = null,
)

data class LaneScheduleResponse(
    val id: UUID,
    val laneId: UUID,
    val departureTime: LocalTime,
    val daysOfWeek: Set<DayOfWeek>,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate?,
    val capacityKg: BigDecimal,
    val status: LaneScheduleStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(schedule: LaneSchedule) = LaneScheduleResponse(
            id = schedule.id,
            laneId = schedule.laneId,
            departureTime = schedule.departureTime,
            daysOfWeek = schedule.daysOfWeek(),
            effectiveFrom = schedule.effectiveFrom,
            effectiveTo = schedule.effectiveTo,
            capacityKg = schedule.capacityKg,
            status = schedule.status,
            createdAt = schedule.createdAt,
            updatedAt = schedule.updatedAt,
        )
    }
}
