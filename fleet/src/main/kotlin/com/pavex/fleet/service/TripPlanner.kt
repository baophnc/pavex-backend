package com.pavex.fleet.service

import com.pavex.contracts.fleet.FleetRouteLegSnapshot
import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.fleet.entity.TripLeg
import java.time.Instant
import java.util.UUID

/** A lane departure the driver has open line-haul work on; every shipment on it rides the same truck. */
data class PlannedDeparture(
    val leg: TripLeg,
    val assignments: List<WorkforceAssignmentSnapshot>,
) {
    val key: DepartureKey get() = DepartureKey(leg.laneId, leg.plannedDepartureAt)

    /** The driver pressed "Start" on at least one shipment, which the DEPART scan requires. */
    val loading: Boolean get() = assignments.any { it.status == OperationsAssignmentStatus.IN_PROGRESS }
}

data class DepartureKey(val laneId: UUID, val plannedDepartureAt: Instant)

object TripPlanner {
    /**
     * Groups a driver's open line-haul assignments by the lane departure their route leg reserved.
     * Assignments whose leg Network no longer knows are left out.
     */
    fun departures(
        assignments: List<WorkforceAssignmentSnapshot>,
        legs: List<FleetRouteLegSnapshot>,
    ): List<PlannedDeparture> {
        val legsById = legs.associateBy { it.routeLegId }
        return assignments
            .filter { it.type == OperationsAssignmentType.LINE_HAUL }
            .mapNotNull { assignment -> assignment.routeLegId?.let(legsById::get)?.let { it to assignment } }
            .groupBy { (leg, _) -> DepartureKey(leg.laneId, leg.departureAt) }
            .map { (_, pairs) -> PlannedDeparture(pairs.first().first.toTripLeg(), pairs.map { it.second }) }
            .sortedBy { it.leg.plannedDepartureAt }
    }

    private fun FleetRouteLegSnapshot.toTripLeg() = TripLeg(
        laneId = laneId,
        laneCode = laneCode,
        fromHubId = fromHub.hubId,
        fromHubCode = fromHub.code,
        fromHubName = fromHub.name,
        toHubId = toHub.hubId,
        toHubCode = toHub.code,
        toHubName = toHub.name,
        plannedDepartureAt = departureAt,
        plannedArrivalAt = arrivalAt,
        distanceKm = distanceKm,
    )
}
