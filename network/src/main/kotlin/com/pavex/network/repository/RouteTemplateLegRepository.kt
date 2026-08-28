package com.pavex.network.repository

import com.pavex.network.entity.RouteTemplateLeg
import com.pavex.network.entity.RouteTemplateStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RouteTemplateLegRepository : JpaRepository<RouteTemplateLeg, UUID> {
    fun findAllByRouteTemplateIdOrderBySequenceAsc(routeTemplateId: UUID): List<RouteTemplateLeg>

    fun findAllByRouteTemplateIdInOrderByRouteTemplateIdAscSequenceAsc(
        routeTemplateIds: Collection<UUID>,
    ): List<RouteTemplateLeg>

    fun deleteAllByRouteTemplateId(routeTemplateId: UUID)

    @Query(
        """
        SELECT CASE WHEN COUNT(leg) > 0 THEN true ELSE false END
        FROM RouteTemplateLeg leg, RouteTemplate template
        WHERE leg.routeTemplateId = template.id
          AND leg.laneId = :laneId
          AND template.status = :status
        """,
    )
    fun existsByLaneIdAndTemplateStatus(
        @Param("laneId") laneId: UUID,
        @Param("status") status: RouteTemplateStatus,
    ): Boolean
}
