package com.pavex.fleet.service

import com.pavex.fleet.entity.FleetTrip
import com.pavex.fleet.entity.FleetTripStatus
import com.pavex.fleet.entity.GeoDistance
import com.pavex.fleet.entity.GeoPoint
import com.pavex.fleet.entity.TripPoint
import com.pavex.fleet.repository.FleetTripRepository
import com.pavex.fleet.store.TripLocationStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** Where a simulated truck is: it left [startedAt] and drives the lane at a steady speed. */
data class SimulatedPosition(
    val point: GeoPoint,
    val headingDeg: Double,
    val travelledMeters: Double,
    val arrived: Boolean,
)

object SimulatedRoute {
    /** Roads are longer than the straight line; used when Network has no lane distance. */
    private const val ROAD_FACTOR = 1.25

    fun position(
        from: GeoPoint,
        to: GeoPoint,
        roadKm: Double?,
        speedKmh: Double,
        startedAt: Instant,
        now: Instant,
    ): SimulatedPosition {
        val straightMeters = GeoDistance.meters(from.lat, from.lng, to.lat, to.lng)
        val roadMeters = roadKm?.takeIf { it > 0 }?.times(1000) ?: (straightMeters * ROAD_FACTOR)
        val hours = Duration.between(startedAt, now).toMillis().coerceAtLeast(0) / 3_600_000.0
        val travelled = (speedKmh * 1000 * hours).coerceAtMost(roadMeters)
        val fraction = if (roadMeters <= 0) 1.0 else travelled / roadMeters
        val point = GeoPoint(from.lat + (to.lat - from.lat) * fraction, from.lng + (to.lng - from.lng) * fraction)
        return SimulatedPosition(point, bearing(from, to), travelled, fraction >= 1.0)
    }

    private fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}

/**
 * Drives every running truck along its lane when no phone reports its position, so the live map,
 * the shipment's truck and the arrival flow can be tried without anyone on the road. Points are
 * stored as mocked. A trip whose phone sent a real fix recently is left alone.
 *
 * Enabled by FLEET_SIMULATOR_ENABLED=true; never in production.
 */
@Component
@ConditionalOnProperty(prefix = "fleet.simulator", name = ["enabled"], havingValue = "true")
class TripSimulator(
    private val trips: FleetTripRepository,
    private val locations: TripLocationStore,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
    @Value("\${fleet.simulator.speed-kmh:60}") private val speedKmh: Double,
    @Value("\${fleet.tracking.signal-lost-after:PT5M}") private val signalLostAfter: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.warn("Fleet simulator is ON: running trucks without a phone move at {} km/h.", speedKmh)
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "\${fleet.simulator.tick:PT30S}")
    fun tick() {
        val now = clock.instant()
        trips.findAllByStatusOrderByStartedAtAsc(FleetTripStatus.IN_PROGRESS, PageRequest.of(0, BATCH_SIZE))
            .filter { simulated(it, now) }
            .forEach { trip ->
                try {
                    transactions.executeWithoutResult { move(trip.id, now) }
                } catch (exception: RuntimeException) {
                    log.warn("Could not move simulated trip {}: {}", trip.id, exception.message)
                }
            }
    }

    private fun simulated(trip: FleetTrip, now: Instant): Boolean {
        if (trip.origin == null || trip.destination == null) return false
        val phoneRecently = !trip.lastMocked && trip.lastReceivedAt?.isAfter(now.minus(signalLostAfter)) == true
        return !phoneRecently
    }

    private fun move(tripId: java.util.UUID, now: Instant) {
        val trip = trips.findLockedById(tripId) ?: return
        if (!trip.inProgress) return
        val from = trip.origin ?: return
        val to = trip.destination ?: return
        // Parked at the destination: nothing new to report until the hub receives the parcels.
        if (trip.lastMocked && trip.lastLat == to.lat && trip.lastLng == to.lng) return
        val position = SimulatedRoute.position(from, to, trip.distanceKm?.toDouble(), speedKmh, trip.startedAt, now)
        val point = TripPoint(
            seq = now.epochSecond,
            recordedAt = now,
            lat = position.point.lat,
            lng = position.point.lng,
            accuracyM = 10.0,
            speedMps = if (position.arrived) 0.0 else speedKmh / 3.6,
            headingDeg = position.headingDeg,
            batteryPct = null,
            mocked = true,
        )
        locations.insert(trip.id, listOf(point), now)
        trip.moveSimulated(point, position.travelledMeters, now)
        if (position.arrived) {
            log.info("Simulated trip {} reached {} after {} km.", trip.id, trip.toHubCode, (position.travelledMeters / 1000).roundToLong())
        }
    }

    companion object {
        private const val BATCH_SIZE = 200
    }
}
