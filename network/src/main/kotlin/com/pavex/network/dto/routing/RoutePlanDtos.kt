package com.pavex.network.dto.routing

import com.pavex.network.entity.TransportMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class RoutePlanResponse(
    val originServiceAreaId: UUID,
    val destinationServiceAreaId: UUID,
    val originHubId: UUID,
    val originHubCode: String,
    val destinationHubId: UUID,
    val destinationHubCode: String,
    val shipmentWeightKg: BigDecimal,
    val readyAt: Instant,
    val arrivalAt: Instant,
    val totalJourneyMinutes: Long,
    val inTransitMinutes: Int,
    val totalDistanceKm: BigDecimal?,
    val legs: List<RoutePlanLegResponse>,
)

data class RoutePlanLegResponse(
    val sequence: Int,
    val laneId: UUID,
    val laneCode: String,
    val scheduleId: UUID,
    val fromHubId: UUID,
    val fromHubCode: String,
    val toHubId: UUID,
    val toHubCode: String,
    val transportMode: TransportMode,
    val serviceDate: LocalDate,
    val scheduledDepartureTime: LocalTime,
    val departureAt: Instant,
    val arrivalAt: Instant,
    val waitMinutes: Long,
    val transitMinutes: Int,
    val distanceKm: BigDecimal?,
)
