package com.pavex.contracts.fleet

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Route legs (network capacity reservations) that Fleet groups into line-haul trips. */
data class FleetRouteLegsRequest(
    val routeLegIds: List<UUID>,
)

data class FleetHubSnapshot(
    val hubId: UUID,
    val code: String,
    val name: String,
    val type: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** One reserved leg: the lane it runs on and when the scheduled departure leaves and arrives. */
data class FleetRouteLegSnapshot(
    val routeLegId: UUID,
    val laneId: UUID,
    val laneCode: String,
    val scheduleId: UUID,
    val departureAt: Instant,
    val arrivalAt: Instant,
    val distanceKm: BigDecimal?,
    val fromHub: FleetHubSnapshot,
    val toHub: FleetHubSnapshot,
)

/** The hubs a worker is an active member of, used to decide which trips they may watch. */
data class FleetHubScopeSnapshot(
    val hubIds: List<UUID>,
)
