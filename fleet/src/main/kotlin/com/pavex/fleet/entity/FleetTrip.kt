package com.pavex.fleet.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FleetTripStatus { IN_PROGRESS, ARRIVED }

/** The driver pressed "Start trip", or scanned the first parcel onto the truck. */
enum class TripStartSource { DRIVER, DEPART_SCAN }

/** The driver pressed "Arrived", or the destination hub received every parcel of the trip. */
enum class TripEndSource { DRIVER, ALL_RECEIVED }

/** One GPS fix sent by the driver's phone; [seq] is the phone's own counter, used to drop resends. */
data class TripPoint(
    val seq: Long,
    val recordedAt: Instant,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val speedMps: Double? = null,
    val headingDeg: Double? = null,
    val batteryPct: Int? = null,
    val mocked: Boolean = false,
)

/** The lane departure a trip runs on, as Network planned it. */
data class TripLeg(
    val laneId: UUID,
    val laneCode: String,
    val fromHubId: UUID,
    val fromHubCode: String,
    val fromHubName: String,
    val toHubId: UUID,
    val toHubCode: String,
    val toHubName: String,
    val plannedDepartureAt: Instant,
    val plannedArrivalAt: Instant,
    val distanceKm: BigDecimal?,
    val from: GeoPoint? = null,
    val to: GeoPoint? = null,
)

data class GeoPoint(val lat: Double, val lng: Double)

/**
 * One truck run of one line-haul driver on one lane departure. It only accepts positions while
 * IN_PROGRESS, keeps the latest fix for the live map, and adds up the distance travelled.
 */
@Entity
@Table(name = "trips", schema = "fleet")
class FleetTrip(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "driver_user_id", nullable = false)
    var driverUserId: UUID = UUID.randomUUID(),
    @Column(name = "driver_email", length = 320)
    var driverEmail: String? = null,
    @Column(name = "lane_id", nullable = false)
    var laneId: UUID = UUID.randomUUID(),
    @Column(name = "lane_code", nullable = false, length = 80)
    var laneCode: String = "",
    @Column(name = "from_hub_id", nullable = false)
    var fromHubId: UUID = UUID.randomUUID(),
    @Column(name = "from_hub_code", nullable = false, length = 50)
    var fromHubCode: String = "",
    @Column(name = "from_hub_name", nullable = false, length = 200)
    var fromHubName: String = "",
    @Column(name = "to_hub_id", nullable = false)
    var toHubId: UUID = UUID.randomUUID(),
    @Column(name = "to_hub_code", nullable = false, length = 50)
    var toHubCode: String = "",
    @Column(name = "to_hub_name", nullable = false, length = 200)
    var toHubName: String = "",
    @Column(name = "planned_departure_at", nullable = false)
    var plannedDepartureAt: Instant = Instant.EPOCH,
    @Column(name = "planned_arrival_at", nullable = false)
    var plannedArrivalAt: Instant = Instant.EPOCH,
    @Column(name = "distance_km", precision = 10, scale = 2)
    var distanceKm: BigDecimal? = null,
    @Column(name = "from_lat")
    var fromLat: Double? = null,
    @Column(name = "from_lng")
    var fromLng: Double? = null,
    @Column(name = "to_lat")
    var toLat: Double? = null,
    @Column(name = "to_lng")
    var toLng: Double? = null,
    @Column(name = "vehicle_plate", length = 20)
    var vehiclePlate: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FleetTripStatus = FleetTripStatus.IN_PROGRESS,
    @Enumerated(EnumType.STRING)
    @Column(name = "start_source", nullable = false, length = 20)
    var startSource: TripStartSource = TripStartSource.DRIVER,
    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.EPOCH,
    @Enumerated(EnumType.STRING)
    @Column(name = "end_source", length = 20)
    var endSource: TripEndSource? = null,
    @Column(name = "arrived_at")
    var arrivedAt: Instant? = null,
    @Column(name = "last_lat")
    var lastLat: Double? = null,
    @Column(name = "last_lng")
    var lastLng: Double? = null,
    @Column(name = "last_accuracy_m")
    var lastAccuracyM: Double? = null,
    @Column(name = "last_speed_mps")
    var lastSpeedMps: Double? = null,
    @Column(name = "last_heading_deg")
    var lastHeadingDeg: Double? = null,
    @Column(name = "last_battery_pct")
    var lastBatteryPct: Int? = null,
    @Column(name = "last_mocked", nullable = false)
    var lastMocked: Boolean = false,
    @Column(name = "last_recorded_at")
    var lastRecordedAt: Instant? = null,
    @Column(name = "last_received_at")
    var lastReceivedAt: Instant? = null,
    @Column(name = "point_count", nullable = false)
    var pointCount: Long = 0,
    @Column(name = "travelled_meters", nullable = false)
    var travelledMeters: Double = 0.0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    val inProgress: Boolean get() = status == FleetTripStatus.IN_PROGRESS

    val origin: GeoPoint? get() = fromLat?.let { lat -> fromLng?.let { GeoPoint(lat, it) } }
    val destination: GeoPoint? get() = toLat?.let { lat -> toLng?.let { GeoPoint(lat, it) } }

    /**
     * Moves a simulated truck: the point is stored like a phone's, but the distance is the share of
     * the road already driven rather than the straight line between fixes, and no speed check applies.
     */
    fun moveSimulated(point: TripPoint, travelledMeters: Double, receivedAt: Instant) {
        pointCount += 1
        lastReceivedAt = receivedAt
        updatedAt = receivedAt
        this.travelledMeters = travelledMeters
        lastLat = point.lat
        lastLng = point.lng
        lastAccuracyM = point.accuracyM
        lastSpeedMps = point.speedMps
        lastHeadingDeg = point.headingDeg
        lastBatteryPct = point.batteryPct
        lastMocked = true
        lastRecordedAt = point.recordedAt
    }

    fun arrive(source: TripEndSource, at: Instant) {
        check(inProgress) { "Trip has already arrived." }
        status = FleetTripStatus.ARRIVED
        endSource = source
        arrivedAt = at
        updatedAt = at
    }

    /**
     * Applies points that were just stored. Only fixes newer than the latest one move the truck and
     * add distance; a late resend or a GPS jump is kept in the raw track but does not move the truck.
     */
    fun applyPoints(stored: List<TripPoint>, receivedAt: Instant) {
        if (stored.isEmpty()) return
        pointCount += stored.size
        lastReceivedAt = receivedAt
        updatedAt = receivedAt
        stored.filter { point -> lastRecordedAt.let { it == null || point.recordedAt.isAfter(it) } }
            .sortedBy { it.recordedAt }
            .forEach { point ->
                val step = step(point) ?: return@forEach
                travelledMeters += step
                lastLat = point.lat
                lastLng = point.lng
                lastAccuracyM = point.accuracyM
                lastSpeedMps = point.speedMps
                lastHeadingDeg = point.headingDeg
                lastBatteryPct = point.batteryPct
                lastMocked = point.mocked
                lastRecordedAt = point.recordedAt
            }
    }

    /**
     * Metres travelled since the previous fix (0 when either fix is too vague to count), or null
     * when reaching this fix would need an impossible speed. The gap grows with time, so a truck
     * that really moved is accepted again after a while.
     */
    private fun step(point: TripPoint): Double? {
        val lat = lastLat ?: return 0.0
        val lng = lastLng ?: return 0.0
        val at = lastRecordedAt ?: return 0.0
        val meters = GeoDistance.meters(lat, lng, point.lat, point.lng)
        val seconds = (point.recordedAt.toEpochMilli() - at.toEpochMilli()) / 1000.0
        if (seconds <= 0 || meters / seconds > MAX_PLAUSIBLE_SPEED_MPS) return null
        val vague = (lastAccuracyM ?: Double.MAX_VALUE) > MAX_ACCURACY_FOR_DISTANCE_M || point.accuracyM > MAX_ACCURACY_FOR_DISTANCE_M
        return if (vague) 0.0 else meters
    }

    companion object {
        /** Fixes vaguer than this still show on the map but do not count towards distance. */
        const val MAX_ACCURACY_FOR_DISTANCE_M = 100.0

        /** About 200 km/h; faster jumps are GPS glitches. */
        const val MAX_PLAUSIBLE_SPEED_MPS = 55.0

        fun start(
            driverUserId: UUID,
            driverEmail: String?,
            leg: TripLeg,
            vehiclePlate: String?,
            source: TripStartSource,
            at: Instant,
        ) = FleetTrip(
            driverUserId = driverUserId,
            driverEmail = driverEmail?.take(320),
            laneId = leg.laneId,
            laneCode = leg.laneCode,
            fromHubId = leg.fromHubId,
            fromHubCode = leg.fromHubCode,
            fromHubName = leg.fromHubName,
            toHubId = leg.toHubId,
            toHubCode = leg.toHubCode,
            toHubName = leg.toHubName,
            plannedDepartureAt = leg.plannedDepartureAt,
            plannedArrivalAt = leg.plannedArrivalAt,
            distanceKm = leg.distanceKm,
            fromLat = leg.from?.lat,
            fromLng = leg.from?.lng,
            toLat = leg.to?.lat,
            toLng = leg.to?.lng,
            vehiclePlate = normalizePlate(vehiclePlate),
            status = FleetTripStatus.IN_PROGRESS,
            startSource = source,
            startedAt = at,
            createdAt = at,
            updatedAt = at,
        )

        /** "51c-123.45" → "51C-123.45"; blank means no plate given. */
        fun normalizePlate(value: String?): String? {
            val plate = value?.trim()?.uppercase()?.replace(Regex("\\s+"), "")?.takeIf { it.isNotEmpty() } ?: return null
            require(plate.length <= 20 && PLATE.matches(plate)) { "Vehicle plate is invalid." }
            return plate
        }

        private val PLATE = Regex("^[0-9A-Z.\\-]{4,20}$")
    }
}

/** Great-circle distance on a spherical Earth, accurate to well under 1% for road trips. */
object GeoDistance {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }
}
