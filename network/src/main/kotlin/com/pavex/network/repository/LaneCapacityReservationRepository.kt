package com.pavex.network.repository

import com.pavex.network.entity.LaneCapacityReservation
import com.pavex.network.entity.LaneCapacityReservationStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface LaneCapacityReservationRepository : JpaRepository<LaneCapacityReservation, UUID> {
    fun findAllByShipmentIdAndRouteRevisionOrderByLegSequenceAsc(
        shipmentId: UUID,
        routeRevision: Int,
    ): List<LaneCapacityReservation>

    fun findAllByScheduleIdInAndServiceDateBetweenAndStatusIn(
        scheduleIds: Collection<UUID>,
        from: LocalDate,
        to: LocalDate,
        statuses: Collection<LaneCapacityReservationStatus>,
    ): List<LaneCapacityReservation>

    fun findAllByScheduleIdInAndServiceDateAndStatusIn(
        scheduleIds: Collection<UUID>,
        serviceDate: LocalDate,
        statuses: Collection<LaneCapacityReservationStatus>,
    ): List<LaneCapacityReservation>

    fun findAllByScheduleIdAndStatusIn(
        scheduleId: UUID,
        statuses: Collection<LaneCapacityReservationStatus>,
    ): List<LaneCapacityReservation>

    fun existsByScheduleIdAndStatusAndDepartureAtAfter(
        scheduleId: UUID,
        status: LaneCapacityReservationStatus,
        departureAt: Instant,
    ): Boolean

    fun existsByLaneIdAndStatusAndDepartureAtAfter(
        laneId: UUID,
        status: LaneCapacityReservationStatus,
        departureAt: Instant,
    ): Boolean
}
