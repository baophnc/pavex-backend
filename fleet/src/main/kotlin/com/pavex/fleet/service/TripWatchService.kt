package com.pavex.fleet.service

import com.pavex.common.security.PavexActor
import com.pavex.fleet.client.OperationsFleetClient
import com.pavex.fleet.config.FleetTrackingProperties
import com.pavex.fleet.dto.DriverTripStatus
import com.pavex.fleet.dto.TrackPointResponse
import com.pavex.fleet.dto.TripSummaryResponse
import com.pavex.fleet.dto.TripTrackResponse
import com.pavex.fleet.entity.FleetTrip
import com.pavex.fleet.entity.FleetTripStatus
import com.pavex.fleet.entity.TripAccessLog
import com.pavex.fleet.exception.FleetForbiddenException
import com.pavex.fleet.exception.FleetNotFoundException
import com.pavex.fleet.repository.FleetTripRepository
import com.pavex.fleet.repository.TripAccessLogRepository
import com.pavex.fleet.store.StoredPoint
import com.pavex.fleet.store.TripLocationStore
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** The hubs whose trips a viewer may watch: every hub for head office, else their memberships. */
data class WatchScope(val allHubs: Boolean, val hubIds: Set<UUID>) {
    fun covers(trip: FleetTrip) = allHubs || trip.fromHubId in hubIds || trip.toHubId in hubIds

    fun covers(hubId: UUID) = allHubs || hubId in hubIds
}

/**
 * Trips for hub managers and head office. A hub manager only sees trips leaving or arriving at a
 * hub they belong to; nobody outside that circle ever gets positions.
 */
@Service
class TripWatchService(
    private val trips: FleetTripRepository,
    private val accessLog: TripAccessLogRepository,
    private val locations: TripLocationStore,
    private val operations: OperationsFleetClient,
    private val properties: FleetTrackingProperties,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(actor: PavexActor, status: DriverTripStatus?, hubId: UUID?): List<TripSummaryResponse> {
        val scope = scope(actor)
        if (hubId != null && !scope.covers(hubId)) {
            throw FleetForbiddenException("hub_out_of_scope", "You can only watch trips of your own hubs.")
        }
        val hubIds = hubId?.let(::setOf) ?: scope.hubIds
        val allHubs = scope.allHubs && hubId == null
        if (!allHubs && hubIds.isEmpty()) return emptyList()
        val now = clock.instant()
        return trips.findVisible(
            status = if (status == DriverTripStatus.ARRIVED) FleetTripStatus.ARRIVED else FleetTripStatus.IN_PROGRESS,
            arrivedAfter = now.minus(RECENT_ARRIVALS),
            allHubs = allHubs,
            hubIds = hubIds.ifEmpty { setOf(NO_HUB) },
            pageable = PageRequest.of(0, MAX_TRIPS),
        ).map { TripViews.summary(it, now, properties.signalLostAfter) }
    }

    @Transactional(readOnly = true)
    fun detail(actor: PavexActor, tripId: UUID): TripSummaryResponse =
        TripViews.summary(visible(actor, tripId), clock.instant(), properties.signalLostAfter)

    @Transactional
    fun track(actor: PavexActor, tripId: UUID): TripTrackResponse {
        val trip = visible(actor, tripId)
        accessLog.save(TripAccessLog(tripId = trip.id, viewerUserId = actor.userId, viewedAt = clock.instant()))
        val points = locations.track(trip.id, MAX_READ_POINTS)
        val thinned = thin(points, MAX_TRACK_POINTS)
        return TripTrackResponse(
            tripId = trip.id,
            points = thinned.map { TrackPointResponse(it.lat, it.lng, it.recordedAt, it.speedMps, it.accuracyM, it.mocked) },
            thinned = thinned.size < points.size,
        )
    }

    private fun visible(actor: PavexActor, tripId: UUID): FleetTrip {
        val trip = trips.findById(tripId).orElse(null)
        // A trip outside the viewer's hubs looks the same as a missing one.
        if (trip == null || !scope(actor).covers(trip)) throw FleetNotFoundException("trip_not_found", "Trip was not found.")
        return trip
    }

    private fun scope(actor: PavexActor): WatchScope =
        if (actor.seesAllHubs) WatchScope(true, emptySet()) else WatchScope(false, operations.hubIds(actor.userId))

    companion object {
        private val NO_HUB = UUID(0, 0)
        private val RECENT_ARRIVALS = Duration.ofHours(24)
        private const val MAX_TRIPS = 500
        private const val MAX_READ_POINTS = 50_000
        private const val MAX_TRACK_POINTS = 2_000

        /** Keeps every n-th point so a long trip fits one response, always keeping the last one. */
        fun thin(points: List<StoredPoint>, max: Int): List<StoredPoint> {
            if (points.size <= max) return points
            val step = (points.size + max - 1) / max
            val kept = points.filterIndexed { index, _ -> index % step == 0 }
            return if (kept.last() == points.last()) kept else kept + points.last()
        }
    }
}
