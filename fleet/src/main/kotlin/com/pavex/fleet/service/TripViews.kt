package com.pavex.fleet.service

import com.pavex.fleet.dto.DriverTripResponse
import com.pavex.fleet.dto.DriverTripStatus
import com.pavex.fleet.dto.HubRef
import com.pavex.fleet.dto.PositionResponse
import com.pavex.fleet.dto.TripSummaryResponse
import com.pavex.fleet.entity.FleetTrip
import com.pavex.fleet.entity.FleetTripStatus
import java.time.Duration
import java.time.Instant

object TripViews {
    fun status(trip: FleetTrip) = when (trip.status) {
        FleetTripStatus.IN_PROGRESS -> DriverTripStatus.IN_PROGRESS
        FleetTripStatus.ARRIVED -> DriverTripStatus.ARRIVED
    }

    fun planned(departure: PlannedDeparture) = DriverTripResponse(
        tripId = null,
        status = DriverTripStatus.PLANNED,
        laneId = departure.leg.laneId,
        laneCode = departure.leg.laneCode,
        fromHub = HubRef(departure.leg.fromHubId, departure.leg.fromHubCode, departure.leg.fromHubName),
        toHub = HubRef(departure.leg.toHubId, departure.leg.toHubCode, departure.leg.toHubName),
        plannedDepartureAt = departure.leg.plannedDepartureAt,
        plannedArrivalAt = departure.leg.plannedArrivalAt,
        distanceKm = departure.leg.distanceKm,
        shipmentCount = departure.assignments.size,
        vehiclePlate = null,
        startedAt = null,
        arrivedAt = null,
    )

    fun driver(trip: FleetTrip, shipmentCount: Int) = DriverTripResponse(
        tripId = trip.id,
        status = status(trip),
        laneId = trip.laneId,
        laneCode = trip.laneCode,
        fromHub = HubRef(trip.fromHubId, trip.fromHubCode, trip.fromHubName),
        toHub = HubRef(trip.toHubId, trip.toHubCode, trip.toHubName),
        plannedDepartureAt = trip.plannedDepartureAt,
        plannedArrivalAt = trip.plannedArrivalAt,
        distanceKm = trip.distanceKm,
        shipmentCount = shipmentCount,
        vehiclePlate = trip.vehiclePlate,
        startedAt = trip.startedAt,
        arrivedAt = trip.arrivedAt,
    )

    fun summary(trip: FleetTrip, now: Instant, signalLostAfter: Duration): TripSummaryResponse {
        val late = trip.inProgress && now.isAfter(trip.plannedArrivalAt)
        val lastHeard = trip.lastReceivedAt ?: trip.startedAt
        return TripSummaryResponse(
            id = trip.id,
            status = status(trip),
            driverUserId = trip.driverUserId,
            driverEmail = trip.driverEmail,
            vehiclePlate = trip.vehiclePlate,
            laneCode = trip.laneCode,
            fromHub = HubRef(trip.fromHubId, trip.fromHubCode, trip.fromHubName),
            toHub = HubRef(trip.toHubId, trip.toHubCode, trip.toHubName),
            plannedDepartureAt = trip.plannedDepartureAt,
            plannedArrivalAt = trip.plannedArrivalAt,
            startedAt = trip.startedAt,
            startSource = trip.startSource.name,
            arrivedAt = trip.arrivedAt,
            endSource = trip.endSource?.name,
            distanceKm = trip.distanceKm,
            travelledKm = Math.round(trip.travelledMeters / 100.0) / 10.0,
            pointCount = trip.pointCount,
            position = position(trip),
            late = late,
            minutesLate = if (late) Duration.between(trip.plannedArrivalAt, now).toMinutes() else null,
            signalLost = trip.inProgress && Duration.between(lastHeard, now) > signalLostAfter,
        )
    }

    private fun position(trip: FleetTrip): PositionResponse? {
        val lat = trip.lastLat ?: return null
        val lng = trip.lastLng ?: return null
        val recordedAt = trip.lastRecordedAt ?: return null
        return PositionResponse(
            lat = lat,
            lng = lng,
            accuracyM = trip.lastAccuracyM,
            speedMps = trip.lastSpeedMps,
            headingDeg = trip.lastHeadingDeg,
            batteryPct = trip.lastBatteryPct,
            mocked = trip.lastMocked,
            recordedAt = recordedAt,
            receivedAt = trip.lastReceivedAt,
        )
    }
}
