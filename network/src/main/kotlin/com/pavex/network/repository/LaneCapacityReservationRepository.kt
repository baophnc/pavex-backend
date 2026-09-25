package com.pavex.network.repository

import com.pavex.network.entity.LaneCapacityReservation
import com.pavex.network.entity.LaneCapacityReservationStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface LaneCapacityReservationRepository : JpaRepository<LaneCapacityReservation, UUID> {
    @Query(
        """
        select distinct r.shipmentId as shipmentId, r.routeRevision as routeRevision
        from LaneCapacityReservation r
        where r.status = com.pavex.network.entity.LaneCapacityReservationStatus.RESERVED
          and r.confirmedAt is null
          and r.confirmationDeadlineAt is not null
          and r.confirmationDeadlineAt <= :now
        """,
    )
    fun findExpiredUnconfirmedReservations(
        @Param("now") now: Instant,
        pageable: Pageable,
    ): List<ReservationIdentity>

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

interface ReservationIdentity {
    val shipmentId: UUID
    val routeRevision: Int
}
