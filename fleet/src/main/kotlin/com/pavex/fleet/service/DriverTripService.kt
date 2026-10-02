package com.pavex.fleet.service

import com.pavex.fleet.client.NetworkFleetClient
import com.pavex.fleet.client.OperationsFleetClient
import com.pavex.fleet.dto.AcceptConsentRequest
import com.pavex.fleet.dto.ConsentResponse
import com.pavex.fleet.dto.DriverTripResponse
import com.pavex.fleet.dto.DriverTripStatus
import com.pavex.fleet.dto.LocationPointRequest
import com.pavex.fleet.dto.RecordLocationsResponse
import com.pavex.fleet.dto.StartTripRequest
import com.pavex.fleet.entity.DriverConsent
import com.pavex.fleet.entity.FleetTrip
import com.pavex.fleet.entity.FleetTripStatus
import com.pavex.fleet.entity.TripEndSource
import com.pavex.fleet.entity.TripPoint
import com.pavex.fleet.entity.TripStartSource
import com.pavex.fleet.exception.FleetConflictException
import com.pavex.fleet.exception.FleetNotFoundException
import com.pavex.fleet.exception.InvalidFleetRequestException
import com.pavex.fleet.repository.DriverConsentRepository
import com.pavex.fleet.repository.FleetTripRepository
import com.pavex.fleet.store.TripLocationStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** What a line-haul driver does in the field app: see today's departures, run a trip, send positions. */
@Service
class DriverTripService(
    private val trips: FleetTripRepository,
    private val consents: DriverConsentRepository,
    private val locations: TripLocationStore,
    private val operations: OperationsFleetClient,
    private val network: NetworkFleetClient,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun consent(userId: UUID): ConsentResponse = consentView(consents.findById(userId).orElse(null))

    @Transactional
    fun acceptConsent(userId: UUID, request: AcceptConsentRequest): ConsentResponse {
        if (request.termsVersion != CURRENT_TERMS_VERSION) {
            throw InvalidFleetRequestException("terms_outdated", "The location terms were updated. Reload them and accept again.")
        }
        val consent = consents.findById(userId).orElse(DriverConsent(userId = userId))
        consent.termsVersion = CURRENT_TERMS_VERSION
        consent.acceptedAt = clock.instant()
        return consentView(consents.save(consent))
    }

    /** Open departures of the driver plus the trips they ran in the last day. */
    @Transactional(readOnly = true)
    fun myTrips(userId: UUID): List<DriverTripResponse> {
        val departures = departures(userId)
        val now = clock.instant()
        val stored = (
            trips.findAllByDriverUserIdAndStartedAtAfterOrderByStartedAtDesc(userId, now.minus(RECENT)) +
                listOfNotNull(trips.findByDriverUserIdAndStatus(userId, FleetTripStatus.IN_PROGRESS))
            ).distinctBy { it.id }
        val storedByKey = stored.associateBy { DepartureKey(it.laneId, it.plannedDepartureAt) }
        val open = departures.map { departure ->
            storedByKey[departure.key]?.let { TripViews.driver(it, departure.assignments.size) } ?: TripViews.planned(departure)
        }
        val openKeys = departures.map { it.key }.toSet()
        val finished = stored.filter { DepartureKey(it.laneId, it.plannedDepartureAt) !in openKeys }.map { TripViews.driver(it, 0) }
        return (open + finished).sortedWith(
            compareBy<DriverTripResponse> { STATUS_ORDER.getValue(it.status) }.thenBy { it.plannedDepartureAt },
        )
    }

    /**
     * Starts the trip from the app. The trip is a record of the truck run and needs no consent;
     * sending its positions does (see [recordLocations]).
     */
    @Transactional
    fun start(userId: UUID, email: String?, request: StartTripRequest): DriverTripResponse {
        val departure = departures(userId).firstOrNull {
            it.leg.laneId == request.laneId && it.leg.plannedDepartureAt == request.plannedDepartureAt
        } ?: throw FleetNotFoundException("trip_not_assigned", "You have no open line-haul work on this departure.")
        return TripViews.driver(startDeparture(userId, email, departure, request.vehiclePlate, TripStartSource.DRIVER), departure.assignments.size)
    }

    /**
     * Called by the app after a successful "load onto truck" scan: starts the trip of the departure
     * being loaded unless one is already running. Returns null when nothing is being loaded.
     */
    @Transactional
    fun startFromScan(userId: UUID, email: String?): DriverTripResponse? {
        trips.findByDriverUserIdAndStatus(userId, FleetTripStatus.IN_PROGRESS)?.let { return TripViews.driver(it, 0) }
        val departure = departures(userId)
            .filter { it.loading }
            .firstOrNull { trips.findByDriverUserIdAndLaneIdAndPlannedDepartureAt(userId, it.leg.laneId, it.leg.plannedDepartureAt) == null }
            ?: return null
        return TripViews.driver(startDeparture(userId, email, departure, null, TripStartSource.DEPART_SCAN), departure.assignments.size)
    }

    /**
     * A parcel was scanned onto a truck: the departure of its route leg is running, whether or not
     * the driver's app is open. Returns null when the leg is unknown or the driver already runs
     * another departure, which is then left to the driver.
     */
    @Transactional
    fun startFromDeparture(driverUserId: UUID, routeLegId: UUID): FleetTrip? {
        val leg = network.routeLegs(listOf(routeLegId)).firstOrNull() ?: return null
        val tripLeg = TripPlanner.leg(leg)
        trips.findByDriverUserIdAndLaneIdAndPlannedDepartureAt(driverUserId, tripLeg.laneId, tripLeg.plannedDepartureAt)?.let { return it }
        if (trips.findByDriverUserIdAndStatus(driverUserId, FleetTripStatus.IN_PROGRESS) != null) return null
        return trips.saveAndFlush(FleetTrip.start(driverUserId, null, tripLeg, null, TripStartSource.DEPART_SCAN, clock.instant()))
    }

    @Transactional
    fun arrive(userId: UUID, tripId: UUID): DriverTripResponse {
        val trip = ownedLocked(userId, tripId)
        if (trip.inProgress) trip.arrive(TripEndSource.DRIVER, clock.instant())
        return TripViews.driver(trip, 0)
    }

    /** Closes a trip whose parcels were all received at the destination hub. */
    @Transactional
    fun closeReceived(tripId: UUID) {
        val trip = trips.findLockedById(tripId) ?: return
        if (trip.inProgress) trip.arrive(TripEndSource.ALL_RECEIVED, clock.instant())
    }

    @Transactional
    fun recordLocations(userId: UUID, tripId: UUID, points: List<LocationPointRequest>): RecordLocationsResponse {
        val trip = ownedLocked(userId, tripId)
        requireConsent(userId)
        if (!trip.inProgress) return RecordLocationsResponse(0, 0, points.size, TripViews.status(trip))
        val now = clock.instant()
        val (valid, rejected) = LocationWindow.split(points.map { it.toPoint() }, trip.startedAt, now)
        val stored = locations.insert(trip.id, valid, now)
        trip.applyPoints(stored, now)
        return RecordLocationsResponse(stored.size, valid.size - stored.size, rejected, DriverTripStatus.IN_PROGRESS)
    }

    private fun startDeparture(
        userId: UUID,
        email: String?,
        departure: PlannedDeparture,
        vehiclePlate: String?,
        source: TripStartSource,
    ): FleetTrip {
        trips.findByDriverUserIdAndLaneIdAndPlannedDepartureAt(userId, departure.leg.laneId, departure.leg.plannedDepartureAt)?.let { existing ->
            if (existing.inProgress) return existing
            throw FleetConflictException("trip_already_arrived", "This departure has already arrived.")
        }
        trips.findByDriverUserIdAndStatus(userId, FleetTripStatus.IN_PROGRESS)?.let {
            throw FleetConflictException("another_trip_in_progress", "Finish the trip ${it.fromHubCode} → ${it.toHubCode} first.")
        }
        val trip = try {
            FleetTrip.start(userId, email, departure.leg, vehiclePlate, source, clock.instant())
        } catch (exception: IllegalArgumentException) {
            throw InvalidFleetRequestException("vehicle_plate_invalid", exception.message ?: "Vehicle plate is invalid.")
        }
        return trips.saveAndFlush(trip)
    }

    private fun departures(userId: UUID): List<PlannedDeparture> {
        val assignments = operations.lineHaulAssignments(userId)
        val legs = network.routeLegs(assignments.mapNotNull { it.routeLegId })
        return TripPlanner.departures(assignments, legs)
    }

    private fun ownedLocked(userId: UUID, tripId: UUID): FleetTrip {
        val trip = trips.findLockedById(tripId)
        if (trip == null || trip.driverUserId != userId) {
            throw FleetNotFoundException("trip_not_found", "Trip was not found.")
        }
        return trip
    }

    private fun requireConsent(userId: UUID) {
        if (consents.findById(userId).orElse(null)?.termsVersion != CURRENT_TERMS_VERSION) {
            throw FleetConflictException("consent_required", "Accept the location-sharing terms before sending the truck's position.")
        }
    }

    private fun consentView(consent: DriverConsent?) = ConsentResponse(
        currentTermsVersion = CURRENT_TERMS_VERSION,
        acceptedVersion = consent?.termsVersion,
        acceptedAt = consent?.acceptedAt,
        required = consent?.termsVersion != CURRENT_TERMS_VERSION,
    )

    private fun LocationPointRequest.toPoint() = TripPoint(
        seq = seq,
        recordedAt = recordedAt,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        speedMps = speedMps,
        headingDeg = headingDeg,
        batteryPct = batteryPct,
        mocked = mocked,
    )

    companion object {
        /** Bump when the location terms shown in the app change; drivers accept again. */
        const val CURRENT_TERMS_VERSION = "2026-09"
        private val RECENT = Duration.ofHours(24)
        private val STATUS_ORDER = mapOf(
            DriverTripStatus.IN_PROGRESS to 0,
            DriverTripStatus.PLANNED to 1,
            DriverTripStatus.ARRIVED to 2,
        )
    }
}

/** Which points of a batch belong to the trip in time: not before it started, not from the future. */
object LocationWindow {
    private val BEFORE_START = Duration.ofMinutes(10)
    private val MAX_AGE = Duration.ofHours(24)
    private val CLOCK_SKEW = Duration.ofMinutes(2)

    /** Returns the valid points (duplicates within the batch removed) and how many were rejected. */
    fun split(points: List<TripPoint>, startedAt: Instant, now: Instant): Pair<List<TripPoint>, Int> {
        val earliest = maxOf(startedAt.minus(BEFORE_START), now.minus(MAX_AGE))
        val latest = now.plus(CLOCK_SKEW)
        val valid = points
            .filter { !it.recordedAt.isBefore(earliest) && !it.recordedAt.isAfter(latest) }
            .distinctBy { it.seq to it.recordedAt }
        return valid to points.count { it.recordedAt.isBefore(earliest) || it.recordedAt.isAfter(latest) }
    }
}
