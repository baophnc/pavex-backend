package com.pavex.network.repository

import com.pavex.network.entity.ServiceArea
import com.pavex.network.entity.ServiceAreaStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ServiceAreaRepository : JpaRepository<ServiceArea, UUID> {
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun existsByRegionIdAndStatus(regionId: UUID, status: ServiceAreaStatus): Boolean
    fun existsByPrimaryHubIdAndStatus(primaryHubId: UUID, status: ServiceAreaStatus): Boolean

    @Query(
        """
        SELECT a FROM ServiceArea a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:regionId IS NULL OR a.regionId = :regionId)
          AND (:search = '' OR
               LOWER(a.code) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
    )
    fun search(
        @Param("status") status: ServiceAreaStatus?,
        @Param("regionId") regionId: UUID?,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<ServiceArea>
}
