package com.pavex.pricing.controller

import com.pavex.pricing.dto.audit.PricingAuditLogResponse
import com.pavex.pricing.dto.audit.PricingPageResponse
import com.pavex.pricing.dto.audit.toPricingResponse
import com.pavex.pricing.entity.PricingAuditEntityType
import com.pavex.pricing.service.PricingAuditService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/pricing/audit")
class PricingAuditController(
    private val audit: PricingAuditService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('pricing.read','pricing.manage')")
    fun search(
        @RequestParam(required = false) entityType: PricingAuditEntityType?,
        @RequestParam(required = false) entityId: UUID?,
        @RequestParam(required = false) actorUserId: UUID?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): PricingPageResponse<PricingAuditLogResponse> =
        audit.search(entityType, entityId, actorUserId, page, pageSize).toPricingResponse(PricingAuditLogResponse::from)

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('pricing.read','pricing.manage')")
    fun get(@PathVariable id: UUID): PricingAuditLogResponse = PricingAuditLogResponse.from(audit.get(id))
}
