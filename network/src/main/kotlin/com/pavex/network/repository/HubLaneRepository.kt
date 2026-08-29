package com.pavex.network.repository

import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.TransportMode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.UUID

interface HubLaneRepository : JpaRepository<HubLane, UUID> {
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun findByCodeIgnoreCase(code: String): HubLane?

    @Query(
        """
        SELECT l FROM HubLane l
        WHERE (:status IS NULL OR l.status = :status)
          AND (:transportMode IS NULL OR l.transportMode = :transportMode)
          AND (:fromHubId IS NULL OR l.fromHubId = :fromHubId)
          AND (:toHubId IS NULL OR l.toHubId = :toHubId)
          AND (:search = '' OR LOWER(l.code) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
    )
    fun searchAdmin(
        @Param("status") status: HubLaneStatus?,
        @Param("transportMode") transportMode: TransportMode?,
        @Param("fromHubId") fromHubId: UUID?,
        @Param("toHubId") toHubId: UUID?,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<HubLane>

    @Query(
        """
        SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
        FROM HubLane l
        WHERE l.status = :status
          AND (l.fromHubId = :hubId OR l.toHubId = :hubId)
        """,
    )
    fun existsByHubIdAndStatus(
        @Param("hubId") hubId: UUID,
        @Param("status") status: HubLaneStatus,
    ): Boolean

    fun findAllByStatusAndMaxShipmentWeightKgGreaterThanEqual(
        status: HubLaneStatus,
        maxShipmentWeightKg: BigDecimal,
    ): List<HubLane>
}
