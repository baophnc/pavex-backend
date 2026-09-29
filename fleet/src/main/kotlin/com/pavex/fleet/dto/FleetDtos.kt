package com.pavex.fleet.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** PLANNED trips exist only as open line-haul assignments; the others are stored trips. */
enum class DriverTripStatus { PLANNED, IN_PROGRESS, ARRIVED }

data class HubRef(
    val id: UUID,
    val code: String,
    val name: String,
)

data class DriverTripResponse(
    val tripId: UUID?,
    val status: DriverTripStatus,
    val laneId: UUID,
    val laneCode: String,
    val fromHub: HubRef,
    val toHub: HubRef,
    val plannedDepartureAt: Instant,
    val plannedArrivalAt: Instant,
    val distanceKm: BigDecimal?,
    /** Open line-haul assignments (shipments) on this departure. */
    val shipmentCount: Int,
    val vehiclePlate: String?,
    val startedAt: Instant?,
    val arrivedAt: Instant?,
)

data class StartTripRequest(
    val laneId: UUID,
    val plannedDepartureAt: Instant,
    @field:Size(max = 20)
    val vehiclePlate: String? = null,
)

data class LocationPointRequest(
    @field:Min(0)
    val seq: Long,
    val recordedAt: Instant,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val lat: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val lng: Double,
    @field:DecimalMin("0.0") @field:DecimalMax("10000.0")
    val accuracyM: Double,
    @field:DecimalMin("0.0") @field:DecimalMax("100.0")
    val speedMps: Double? = null,
    @field:DecimalMin("0.0") @field:DecimalMax("360.0")
    val headingDeg: Double? = null,
    @field:Min(0) @field:Max(100)
    val batteryPct: Int? = null,
    val mocked: Boolean = false,
)

data class RecordLocationsRequest(
    @field:Size(min = 1, max = 200)
    val points: List<@Valid LocationPointRequest>,
)

data class RecordLocationsResponse(
    val accepted: Int,
    val duplicates: Int,
    /** Points outside the trip's time window (clock far off, or recorded before the trip started). */
    val rejected: Int,
    /** When this is no longer IN_PROGRESS the app stops sending positions. */
    val tripStatus: DriverTripStatus,
)

data class ConsentResponse(
    val currentTermsVersion: String,
    val acceptedVersion: String?,
    val acceptedAt: Instant?,
    val required: Boolean,
)

data class AcceptConsentRequest(
    @field:NotBlank @field:Size(max = 20)
    val termsVersion: String,
)

data class PositionResponse(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double?,
    val speedMps: Double?,
    val headingDeg: Double?,
    val batteryPct: Int?,
    val mocked: Boolean,
    val recordedAt: Instant,
    val receivedAt: Instant?,
)

data class TripSummaryResponse(
    val id: UUID,
    val status: DriverTripStatus,
    val driverUserId: UUID,
    val driverEmail: String?,
    val vehiclePlate: String?,
    val laneCode: String,
    val fromHub: HubRef,
    val toHub: HubRef,
    val plannedDepartureAt: Instant,
    val plannedArrivalAt: Instant,
    val startedAt: Instant,
    val startSource: String,
    val arrivedAt: Instant?,
    val endSource: String?,
    val distanceKm: BigDecimal?,
    val travelledKm: Double,
    val pointCount: Long,
    val position: PositionResponse?,
    /** Still on the road after the planned arrival time. */
    val late: Boolean,
    val minutesLate: Long?,
    /** In progress but no position received for a while. */
    val signalLost: Boolean,
)

data class TrackPointResponse(
    val lat: Double,
    val lng: Double,
    val recordedAt: Instant,
    val speedMps: Double?,
    val accuracyM: Double,
    val mocked: Boolean,
)

data class TripTrackResponse(
    val tripId: UUID,
    val points: List<TrackPointResponse>,
    /** The trip has more points than one response carries; the points are evenly thinned out. */
    val thinned: Boolean,
)
