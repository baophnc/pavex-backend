package com.pavex.fleet.service

import com.pavex.fleet.client.NetworkFleetClient
import com.pavex.fleet.client.OperationsFleetClient
import com.pavex.fleet.entity.FleetTripStatus
import com.pavex.fleet.exception.FleetUnavailableException
import com.pavex.fleet.repository.FleetTripRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Ends trips on their own once the destination hub has received every parcel: receiving the last
 * parcel completes the driver's line-haul assignments, so the departure drops out of their open work.
 */
@Component
class TripArrivalMonitor(
    private val trips: FleetTripRepository,
    private val operations: OperationsFleetClient,
    private val network: NetworkFleetClient,
    private val driverTrips: DriverTripService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "\${fleet.tracking.arrival-check-interval:PT1M}")
    fun closeReceivedTrips() {
        trips.findAllByStatusOrderByStartedAtAsc(FleetTripStatus.IN_PROGRESS, PageRequest.of(0, BATCH_SIZE))
            .groupBy { it.driverUserId }
            .forEach { (driverUserId, driverTrips) ->
                val open = try {
                    val assignments = operations.lineHaulAssignments(driverUserId)
                    TripPlanner.departures(assignments, network.routeLegs(assignments.mapNotNull { it.routeLegId }))
                        .map { it.key }
                        .toSet()
                } catch (exception: FleetUnavailableException) {
                    log.debug("Skipping arrival check for driver {}: {}", driverUserId, exception.code)
                    return@forEach
                }
                driverTrips
                    .filter { DepartureKey(it.laneId, it.plannedDepartureAt) !in open }
                    .forEach { this.driverTrips.closeReceived(it.id) }
            }
    }

    companion object {
        private const val BATCH_SIZE = 200
    }
}
