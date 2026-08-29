package com.pavex.pricing.repository

import com.pavex.pricing.entity.PricingAuditEntityType
import com.pavex.pricing.entity.PricingAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PricingAuditLogRepository : JpaRepository<PricingAuditLog, UUID> {
    @Query(
        """
        select a from PricingAuditLog a
        where (:entityType is null or a.entityType = :entityType)
          and (:entityId is null or a.entityId = :entityId)
          and (:actorUserId is null or a.actorUserId = :actorUserId)
        order by a.occurredAt desc
        """,
    )
    fun search(
        @Param("entityType") entityType: PricingAuditEntityType?,
        @Param("entityId") entityId: UUID?,
        @Param("actorUserId") actorUserId: UUID?,
        pageable: Pageable,
    ): Page<PricingAuditLog>
}
