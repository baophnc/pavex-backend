package com.pavex.network.controller

import com.pavex.contracts.fleet.FleetHubSnapshot
import com.pavex.contracts.fleet.FleetRouteLegSnapshot
import com.pavex.contracts.fleet.FleetRouteLegsRequest
import com.pavex.network.entity.Hub
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.HubRepository
import com.pavex.network.repository.LaneCapacityReservationRepository
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Lane, hubs and planned times of line-haul route legs, so Fleet can group a driver's legs into trips. */
@Hidden
@RestController
@RequestMapping("/internal/v1/network/fleet")
class InternalFleetNetworkController(
    private val reservations: LaneCapacityReservationRepository,
    private val lanes: HubLaneRepository,
    private val hubs: HubRepository,
) {
    @PostMapping("/route-legs")
    @Transactional(readOnly = true)
    fun routeLegs(@RequestBody request: FleetRouteLegsRequest): List<FleetRouteLegSnapshot> {
        val ids = request.routeLegIds.distinct().take(MAX_BATCH_SIZE)
        if (ids.isEmpty()) return emptyList()
        val legs = reservations.findAllById(ids)
        val lanesById = lanes.findAllById(legs.map { it.laneId }.distinct()).associateBy { it.id }
        val hubIds = lanesById.values.flatMap { listOf(it.fromHubId, it.toHubId) }.distinct()
        val hubsById = hubs.findAllById(hubIds).associateBy { it.id }
        return legs.mapNotNull { leg ->
            val lane = lanesById[leg.laneId] ?: return@mapNotNull null
            val from = hubsById[lane.fromHubId] ?: return@mapNotNull null
            val to = hubsById[lane.toHubId] ?: return@mapNotNull null
            FleetRouteLegSnapshot(
                routeLegId = leg.id,
                laneId = lane.id,
                laneCode = lane.code,
                scheduleId = leg.scheduleId,
                departureAt = leg.departureAt,
                arrivalAt = leg.arrivalAt,
                distanceKm = lane.distanceKm,
                fromHub = from.toSnapshot(),
                toHub = to.toSnapshot(),
            )
        }
    }

    private fun Hub.toSnapshot() = FleetHubSnapshot(id, code, name, type.name, latitude, longitude)

    companion object {
        private const val MAX_BATCH_SIZE = 500
    }
}
