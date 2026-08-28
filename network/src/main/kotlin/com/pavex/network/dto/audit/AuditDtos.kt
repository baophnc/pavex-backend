package com.pavex.network.dto.audit

import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.NetworkAuditLog
import java.time.Instant
import java.util.UUID

data class AuditLogResponse(
    val id: UUID,
    val entityType: NetworkAuditEntityType,
    val entityId: UUID,
    val action: NetworkAuditAction,
    val actorUserId: UUID,
    val beforeJson: String?,
    val afterJson: String?,
    val reason: String?,
    val occurredAt: Instant,
) {
    companion object {
        fun from(log: NetworkAuditLog) = AuditLogResponse(
            log.id, log.entityType, log.entityId, log.action, log.actorUserId,
            log.beforeJson, log.afterJson, log.reason, log.occurredAt,
        )
    }
}
