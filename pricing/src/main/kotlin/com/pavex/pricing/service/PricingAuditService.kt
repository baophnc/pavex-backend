package com.pavex.pricing.service

import com.pavex.common.api.OneBasedPagination
import com.pavex.pricing.entity.PricingAuditAction
import com.pavex.pricing.entity.PricingAuditEntityType
import com.pavex.pricing.entity.PricingAuditLog
import com.pavex.pricing.exception.NotFoundException
import com.pavex.pricing.repository.PricingAuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class PricingAuditService(
    private val auditLogs: PricingAuditLogRepository,
    private val objectMapper: ObjectMapper,
) {
    fun record(
        entityType: PricingAuditEntityType,
        entityId: UUID,
        action: PricingAuditAction,
        actorUserId: UUID,
        before: Any? = null,
        after: Any? = null,
        reason: String? = null,
    ) {
        auditLogs.save(
            PricingAuditLog(
                entityType = entityType,
                entityId = entityId,
                action = action,
                actorUserId = actorUserId,
                beforeJson = before?.let(objectMapper::writeValueAsString),
                afterJson = after?.let(objectMapper::writeValueAsString),
                reason = reason?.trim()?.takeIf(String::isNotBlank)?.take(500),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): PricingAuditLog = auditLogs.findById(id).orElseThrow {
        NotFoundException("audit_log_not_found", "Pricing audit log was not found.")
    }

    @Transactional(readOnly = true)
    fun search(
        entityType: PricingAuditEntityType?,
        entityId: UUID?,
        actorUserId: UUID?,
        page: Int,
        pageSize: Int,
    ) = OneBasedPagination.normalize(page, pageSize).run {
        auditLogs.search(
            entityType,
            entityId,
            actorUserId,
            PageRequest.of(zeroBasedPage, pageSize),
        )
    }
}
