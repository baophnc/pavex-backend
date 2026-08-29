package com.pavex.network.repository

import com.pavex.network.entity.RouteTemplate
import com.pavex.network.entity.RouteTemplateStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RouteTemplateRepository : JpaRepository<RouteTemplate, UUID> {
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun findByCodeIgnoreCase(code: String): RouteTemplate?

    fun findAllByOriginHubIdAndDestinationHubIdAndStatusOrderByPriorityAscCodeAsc(
        originHubId: UUID,
        destinationHubId: UUID,
        status: RouteTemplateStatus,
    ): List<RouteTemplate>

    @Query(
        """
        SELECT t FROM RouteTemplate t
        WHERE (:status IS NULL OR t.status = :status)
          AND (:originHubId IS NULL OR t.originHubId = :originHubId)
          AND (:destinationHubId IS NULL OR t.destinationHubId = :destinationHubId)
          AND (:search = '' OR LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
    )
    fun searchAdmin(
        @Param("status") status: RouteTemplateStatus?,
        @Param("originHubId") originHubId: UUID?,
        @Param("destinationHubId") destinationHubId: UUID?,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<RouteTemplate>
}
