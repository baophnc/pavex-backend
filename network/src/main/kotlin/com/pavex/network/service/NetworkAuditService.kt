package com.pavex.network.service

import tools.jackson.databind.ObjectMapper
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.NetworkAuditLog
import com.pavex.network.repository.NetworkAuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NetworkAuditService(
    private val auditLogs: NetworkAuditLogRepository,
    private val objectMapper: ObjectMapper,
) {
    fun record(
        entityType: NetworkAuditEntityType,
        entityId: UUID,
        action: NetworkAuditAction,
        actorUserId: UUID,
        before: Any? = null,
        after: Any? = null,
        reason: String? = null,
    ) {
        auditLogs.save(
            NetworkAuditLog(
                entityType = entityType,
                entityId = entityId,
                action = action,
                actorUserId = actorUserId,
                beforeJson = before?.let { objectMapper.writeValueAsString(it) },
                afterJson = after?.let { objectMapper.writeValueAsString(it) },
                reason = reason?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): NetworkAuditLog = auditLogs.findById(id).orElseThrow {
        com.pavex.network.exception.NotFoundException("audit_log_not_found", "Audit log was not found.")
    }

    @Transactional(readOnly = true)
    fun search(
        entityType: NetworkAuditEntityType?,
        entityId: UUID?,
        actorUserId: UUID?,
        page: Int,
        pageSize: Int,
    ) = auditLogs.search(
        entityType,
        entityId,
        actorUserId,
        PageRequest.of((page.coerceAtLeast(1) - 1), pageSize.coerceIn(1, 100)),
    )
}
