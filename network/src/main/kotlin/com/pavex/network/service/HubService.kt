package com.pavex.network.service

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.hub.CreateHubRequest
import com.pavex.network.dto.hub.HubResponse
import com.pavex.network.dto.hub.PublicHubResponse
import com.pavex.network.dto.hub.UpdateHubRequest
import com.pavex.network.dto.hub.UpdateHubStatusRequest
import com.pavex.network.dto.toResponse
import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.HubType
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.ServiceAreaStatus
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubRepository
import com.pavex.network.repository.ServiceAreaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class HubService(
    private val hubs: HubRepository,
    private val serviceAreas: ServiceAreaRepository,
    private val locations: LocationCatalogClient,
    private val hierarchy: HubHierarchyService,
    private val audit: NetworkAuditService,
) {
    @Transactional(readOnly = true)
    fun listAdmin(
        status: HubStatus?,
        type: HubType?,
        provinceCode: String?,
        search: String?,
        page: Int,
        pageSize: Int,
    ): PageResponse<HubResponse> = hubs.searchAdmin(
        status,
        type,
        provinceCode?.trim().orEmpty(),
        search?.trim().orEmpty(),
        PageRequest.of((page.coerceAtLeast(1) - 1), pageSize.coerceIn(1, 100), Sort.by(Sort.Direction.ASC, "code")),
    ).toResponse(HubResponse::from)

    @Transactional(readOnly = true)
    fun listPublic(provinceCode: String?, search: String?, page: Int, pageSize: Int): PageResponse<PublicHubResponse> =
        hubs.searchPublic(
            HubStatus.ACTIVE,
            provinceCode?.trim().orEmpty(),
            search?.trim().orEmpty(),
            PageRequest.of((page.coerceAtLeast(1) - 1), pageSize.coerceIn(1, 100), Sort.by(Sort.Direction.ASC, "name")),
        ).toResponse(PublicHubResponse::from)

    @Transactional(readOnly = true)
    fun get(id: UUID): HubResponse = HubResponse.from(find(id))

    @Transactional
    fun create(request: CreateHubRequest, actorUserId: UUID): HubResponse {
        val code = request.code.trim().uppercase()
        if (hubs.existsByCodeIgnoreCase(code)) {
            throw ConflictException("hub_code_exists", "Hub code already exists.")
        }
        locations.validate(request.provinceCode, request.wardCode)
        hierarchy.validateParent(null, request.parentHubId)
        val hub = hubs.save(
            Hub(
                code = code,
                name = request.name.trim(),
                type = request.type,
                addressLine = request.addressLine.trim(),
                provinceCode = request.provinceCode.trim(),
                wardCode = request.wardCode.trim(),
                parentHubId = request.parentHubId,
                publicVisible = request.publicVisible,
                publicName = request.publicName?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
        audit.record(NetworkAuditEntityType.HUB, hub.id, NetworkAuditAction.CREATED, actorUserId, after = HubResponse.from(hub))
        return HubResponse.from(hub)
    }

    @Transactional
    fun update(id: UUID, request: UpdateHubRequest, actorUserId: UUID): HubResponse {
        val hub = find(id)
        val before = HubResponse.from(hub)
        locations.validate(request.provinceCode, request.wardCode)
        hierarchy.validateParent(id, request.parentHubId)
        hub.update(
            request.name,
            request.type,
            request.addressLine,
            request.provinceCode,
            request.wardCode,
            request.parentHubId,
            request.publicVisible,
            request.publicName,
        )
        val saved = hubs.save(hub)
        audit.record(NetworkAuditEntityType.HUB, id, NetworkAuditAction.UPDATED, actorUserId, before, HubResponse.from(saved))
        return HubResponse.from(saved)
    }

    @Transactional
    fun updateStatus(id: UUID, request: UpdateHubStatusRequest, actorUserId: UUID): HubResponse {
        val hub = find(id)
        if (!request.active && serviceAreas.existsByPrimaryHubIdAndStatus(id, ServiceAreaStatus.ACTIVE)) {
            throw ConflictException("hub_in_use", "Deactivate or reassign active service areas before disabling this hub.")
        }
        val before = HubResponse.from(hub)
        hub.setActive(request.active)
        val saved = hubs.save(hub)
        audit.record(
            NetworkAuditEntityType.HUB,
            id,
            if (request.active) NetworkAuditAction.ACTIVATED else NetworkAuditAction.DEACTIVATED,
            actorUserId,
            before,
            HubResponse.from(saved),
            request.reason,
        )
        return HubResponse.from(saved)
    }

    fun find(id: UUID): Hub = hubs.findById(id).orElseThrow {
        NotFoundException("hub_not_found", "Hub was not found.")
    }
}
