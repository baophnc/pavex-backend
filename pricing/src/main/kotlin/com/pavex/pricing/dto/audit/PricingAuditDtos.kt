package com.pavex.pricing.dto.audit

import com.pavex.pricing.entity.PricingAuditAction
import com.pavex.pricing.entity.PricingAuditEntityType
import com.pavex.pricing.entity.PricingAuditLog
import org.springframework.data.domain.Page
import java.time.Instant
import java.util.UUID

data class PricingAuditLogResponse(
    val id: UUID,
    val entityType: PricingAuditEntityType,
    val entityId: UUID,
    val action: PricingAuditAction,
    val actorUserId: UUID,
    val beforeJson: String?,
    val afterJson: String?,
    val reason: String?,
    val occurredAt: Instant,
) {
    companion object {
        fun from(log: PricingAuditLog) = PricingAuditLogResponse(
            log.id,
            log.entityType,
            log.entityId,
            log.action,
            log.actorUserId,
            log.beforeJson,
            log.afterJson,
            log.reason,
            log.occurredAt,
        )
    }
}

data class PricingPageResponse<T : Any>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
)

fun <T : Any, R : Any> Page<T>.toPricingResponse(mapper: (T) -> R): PricingPageResponse<R> = PricingPageResponse(
    items = content.map(mapper),
    page = number + 1,
    pageSize = size,
    totalItems = totalElements,
    totalPages = totalPages,
)
