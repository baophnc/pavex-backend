package com.pavex.network.service

import com.pavex.network.entity.HubLane
import com.pavex.network.entity.LaneSchedule
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

object RouteTemplateJourneyPlanner {
    fun plan(
        readyAt: Instant,
        lanes: List<HubLane>,
        schedules: List<LaneSchedule>,
        transferBufferMinutes: Int,
        scheduleSearchDays: Int,
        shipmentWeightKg: BigDecimal = BigDecimal.ZERO,
        capacity: DepartureCapacitySnapshot = DepartureCapacitySnapshot.EMPTY,
    ): List<PlannedGraphLeg>? {
        require(transferBufferMinutes >= 0) { "Transfer buffer cannot be negative." }
        if (lanes.isEmpty()) return emptyList()

        val schedulesByLane = schedules.groupBy { it.laneId }
        val result = mutableListOf<PlannedGraphLeg>()
        var currentArrival = readyAt

        lanes.forEachIndexed { index, lane ->
            val departureReadyAt = if (index == 0) {
                currentArrival
            } else {
                currentArrival.plusSeconds(transferBufferMinutes.toLong() * 60)
            }
            val next = schedulesByLane[lane.id]
                .orEmpty()
                .mapNotNull { schedule ->
                    LaneScheduleCalculator.nextDeparture(
                        schedule,
                        departureReadyAt,
                        scheduleSearchDays,
                    ) { departure ->
                        shipmentWeightKg.signum() <= 0 ||
                            capacity.canCarry(schedule, departure.serviceDate, shipmentWeightKg)
                    }?.let { schedule to it }
                }
                .minByOrNull { (_, departure) -> departure.departureAt }
                ?: return null

            val (schedule, departure) = next
            val arrivalAt = departure.departureAt.plusSeconds(lane.estimatedTransitMinutes.toLong() * 60)
            result += PlannedGraphLeg(
                lane = lane,
                schedule = schedule,
                serviceDate = departure.serviceDate,
                departureAt = departure.departureAt,
                arrivalAt = arrivalAt,
                waitMinutes = Duration.between(currentArrival, departure.departureAt).toMinutes(),
            )
            currentArrival = arrivalAt
        }

        return result
    }
}
