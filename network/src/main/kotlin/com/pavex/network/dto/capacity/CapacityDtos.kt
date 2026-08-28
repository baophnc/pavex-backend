package com.pavex.network.dto.capacity

import com.pavex.network.dto.routing.RoutePlanSource
import com.pavex.network.dto.routing.RoutePlanningMode
import com.pavex.network.entity.LaneCapacityReservationStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ReserveLaneCapacityRequest(
    val shipmentId: UUID,
    @field:Min(1)
    val routeRevision: Int,
    @field:NotBlank
    val originProvinceCode: String,
    @field:NotBlank
    val originWardCode: String,
    @field:NotBlank
    val destinationProvinceCode: String,
    @field:NotBlank
    val destinationWardCode: String,
    @field:DecimalMin(value = "0.001")
    val shipmentWeightKg: BigDecimal,
    val readyAt: Instant,
    val mode: RoutePlanningMode = RoutePlanningMode.PREFER_TEMPLATE,
)

enum class CapacityReservationBatchStatus {
    RESERVED,
    IN_TRANSIT,
    CONSUMED,
    RELEASED,
    PARTIALLY_CONSUMED,
    NOT_REQUIRED,
}

data class CapacityReservationResponse(
    val batchId: UUID?,
    val shipmentId: UUID,
    val routeRevision: Int,
    val shipmentWeightKg: BigDecimal,
    val estimatedDeliveryAt: Instant,
    val source: RoutePlanSource,
    val routeTemplateId: UUID? = null,
    val routeTemplateCode: String? = null,
    val routeTemplateRevision: Int? = null,
    val status: CapacityReservationBatchStatus,
    val legs: List<CapacityReservationLegResponse>,
)

data class CapacityReservationLegResponse(
    val reservationId: UUID,
    val sequence: Int,
    val laneId: UUID,
    val laneCode: String,
    val scheduleId: UUID,
    val serviceDate: LocalDate,
    val departureAt: Instant,
    val arrivalAt: Instant,
    val departureCapacityKg: BigDecimal,
    val reservedWeightKg: BigDecimal,
    val status: LaneCapacityReservationStatus,
)

data class LaneDepartureCapacityResponse(
    val scheduleId: UUID,
    val departureTime: LocalTime,
    val serviceDate: LocalDate,
    val capacityKg: BigDecimal,
    val allocatedCapacityKg: BigDecimal,
    val remainingCapacityKg: BigDecimal,
)

data class LaneCapacityResponse(
    val laneId: UUID,
    val laneCode: String,
    val serviceDate: LocalDate,
    val totalCapacityKg: BigDecimal,
    val allocatedCapacityKg: BigDecimal,
    val remainingCapacityKg: BigDecimal,
    val departures: List<LaneDepartureCapacityResponse>,
)
