package com.pavex.network.service

import com.pavex.network.entity.HubLane
import com.pavex.network.entity.LaneSchedule
import java.time.Duration
import java.time.Instant
import java.util.PriorityQueue
import java.util.UUID

object EarliestArrivalPathFinder {
    fun find(
        originHubId: UUID,
        destinationHubId: UUID,
        readyAt: Instant,
        lanes: List<HubLane>,
        schedules: List<LaneSchedule>,
        transferBufferMinutes: Int,
        scheduleSearchDays: Int,
    ): List<PlannedGraphLeg>? {
        require(transferBufferMinutes >= 0) { "Transfer buffer cannot be negative." }
        if (originHubId == destinationHubId) return emptyList()

        val schedulesByLane = schedules.groupBy { it.laneId }
        val outgoing = lanes.groupBy { it.fromHubId }
        val bestArrival = mutableMapOf(originHubId to readyAt)
        val previous = mutableMapOf<UUID, PlannedGraphLeg>()
        val queue = PriorityQueue<QueueState>(compareBy { it.arrivalAt })
        queue += QueueState(originHubId, readyAt)

        while (queue.isNotEmpty()) {
            val state = queue.remove()
            if (bestArrival[state.hubId] != state.arrivalAt) continue
            if (state.hubId == destinationHubId) break

            for (lane in outgoing[state.hubId].orEmpty()) {
                val departureReadyAt = if (state.hubId == originHubId && state.arrivalAt == readyAt) {
                    state.arrivalAt
                } else {
                    state.arrivalAt.plusSeconds(transferBufferMinutes.toLong() * 60)
                }

                val next = schedulesByLane[lane.id]
                    .orEmpty()
                    .mapNotNull { schedule ->
                        LaneScheduleCalculator.nextDeparture(schedule, departureReadyAt, scheduleSearchDays)
                            ?.let { schedule to it }
                    }
                    .minByOrNull { (_, departure) -> departure.departureAt }
                    ?: continue

                val (schedule, departure) = next
                val arrivalAt = departure.departureAt.plusSeconds(lane.estimatedTransitMinutes.toLong() * 60)
                val knownArrival = bestArrival[lane.toHubId]
                if (knownArrival != null && !arrivalAt.isBefore(knownArrival)) continue

                val leg = PlannedGraphLeg(
                    lane = lane,
                    schedule = schedule,
                    serviceDate = departure.serviceDate,
                    departureAt = departure.departureAt,
                    arrivalAt = arrivalAt,
                    waitMinutes = Duration.between(state.arrivalAt, departure.departureAt).toMinutes(),
                )
                bestArrival[lane.toHubId] = arrivalAt
                previous[lane.toHubId] = leg
                queue += QueueState(lane.toHubId, arrivalAt)
            }
        }

        if (destinationHubId !in previous) return null

        val result = mutableListOf<PlannedGraphLeg>()
        var currentHubId = destinationHubId
        while (currentHubId != originHubId) {
            val leg = previous[currentHubId] ?: return null
            result += leg
            currentHubId = leg.lane.fromHubId
        }
        result.reverse()
        return result
    }

    private data class QueueState(val hubId: UUID, val arrivalAt: Instant)
}

data class PlannedGraphLeg(
    val lane: HubLane,
    val schedule: LaneSchedule,
    val serviceDate: java.time.LocalDate,
    val departureAt: Instant,
    val arrivalAt: Instant,
    val waitMinutes: Long,
)
