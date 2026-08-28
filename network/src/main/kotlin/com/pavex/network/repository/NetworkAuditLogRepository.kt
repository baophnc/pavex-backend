package com.pavex.network.repository

import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.NetworkAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NetworkAuditLogRepository : JpaRepository<NetworkAuditLog, UUID> {
    @Query(
        """
        SELECT a FROM NetworkAuditLog a
        WHERE (:entityType IS NULL OR a.entityType = :entityType)
          AND (:entityId IS NULL OR a.entityId = :entityId)
          AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
        ORDER BY a.occurredAt DESC
        """,
    )
    fun search(
        @Param("entityType") entityType: NetworkAuditEntityType?,
        @Param("entityId") entityId: UUID?,
        @Param("actorUserId") actorUserId: UUID?,
        pageable: Pageable,
    ): Page<NetworkAuditLog>
}
