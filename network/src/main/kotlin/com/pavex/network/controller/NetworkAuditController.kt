package com.pavex.network.controller

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.audit.AuditLogResponse
import com.pavex.network.dto.toResponse
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.service.NetworkAuditService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/network/audit")
class NetworkAuditController(private val audit: NetworkAuditService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun search(
        @RequestParam(required = false) entityType: NetworkAuditEntityType?,
        @RequestParam(required = false) entityId: UUID?,
        @RequestParam(required = false) actorUserId: UUID?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): PageResponse<AuditLogResponse> = audit.search(entityType, entityId, actorUserId, page, pageSize).toResponse(AuditLogResponse::from)

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun get(@PathVariable id: UUID): AuditLogResponse = AuditLogResponse.from(audit.get(id))
}
