package com.pavex.fleet.repository

import com.pavex.fleet.entity.DriverConsent
import com.pavex.fleet.entity.FleetTrip
import com.pavex.fleet.entity.FleetTripStatus
import com.pavex.fleet.entity.TripAccessLog
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface FleetTripRepository : JpaRepository<FleetTrip, UUID> {
    /** Serialises position batches and arrival of one trip. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM FleetTrip t WHERE t.id = :id")
    fun findLockedById(@Param("id") id: UUID): FleetTrip?

    fun findByDriverUserIdAndStatus(driverUserId: UUID, status: FleetTripStatus): FleetTrip?

    fun findByDriverUserIdAndLaneIdAndPlannedDepartureAt(driverUserId: UUID, laneId: UUID, plannedDepartureAt: Instant): FleetTrip?

    fun findAllByDriverUserIdAndStartedAtAfterOrderByStartedAtDesc(driverUserId: UUID, after: Instant): List<FleetTrip>

    fun findAllByLaneIdAndPlannedDepartureAt(laneId: UUID, plannedDepartureAt: Instant): List<FleetTrip>

    fun findAllByStatusOrderByStartedAtAsc(status: FleetTripStatus, pageable: Pageable): List<FleetTrip>

    @Query(
        """
        SELECT t FROM FleetTrip t
        WHERE t.status = :status
          AND (t.status = com.pavex.fleet.entity.FleetTripStatus.IN_PROGRESS OR t.arrivedAt >= :arrivedAfter)
          AND (:allHubs = TRUE OR t.fromHubId IN :hubIds OR t.toHubId IN :hubIds)
        ORDER BY t.plannedDepartureAt DESC
        """,
    )
    fun findVisible(
        @Param("status") status: FleetTripStatus,
        @Param("arrivedAfter") arrivedAfter: Instant,
        @Param("allHubs") allHubs: Boolean,
        @Param("hubIds") hubIds: Collection<UUID>,
        pageable: Pageable,
    ): List<FleetTrip>
}

interface DriverConsentRepository : JpaRepository<DriverConsent, UUID>

interface TripAccessLogRepository : JpaRepository<TripAccessLog, UUID>
