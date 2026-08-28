package com.pavex.network.service

import com.pavex.network.dto.region.CreateRegionRequest
import com.pavex.network.dto.region.RegionResponse
import com.pavex.network.dto.region.UpdateRegionRequest
import com.pavex.network.dto.region.UpdateRegionStatusRequest
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.NetworkRegion
import com.pavex.network.entity.NetworkRegionStatus
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.NetworkRegionRepository
import com.pavex.network.repository.ServiceAreaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NetworkRegionService(
    private val regions: NetworkRegionRepository,
    private val serviceAreas: ServiceAreaRepository,
    private val audit: NetworkAuditService,
) {
    @Transactional(readOnly = true)
    fun list(activeOnly: Boolean): List<RegionResponse> =
        (if (activeOnly) regions.findAllByStatusOrderByNameAsc(NetworkRegionStatus.ACTIVE)
        else regions.findAllByOrderByNameAsc()).map(RegionResponse::from)

    @Transactional(readOnly = true)
    fun get(id: UUID): RegionResponse = RegionResponse.from(find(id))

    @Transactional
    fun create(request: CreateRegionRequest, actorUserId: UUID): RegionResponse {
        val code = request.code.trim().uppercase()
        if (regions.existsByCodeIgnoreCase(code)) {
            throw ConflictException("region_code_exists", "Network region code already exists.")
        }
        val region = regions.save(NetworkRegion(code = code, name = request.name.trim()))
        audit.record(NetworkAuditEntityType.REGION, region.id, NetworkAuditAction.CREATED, actorUserId, after = RegionResponse.from(region))
        return RegionResponse.from(region)
    }

    @Transactional
    fun update(id: UUID, request: UpdateRegionRequest, actorUserId: UUID): RegionResponse {
        val region = find(id)
        val before = RegionResponse.from(region)
        region.update(request.name)
        val saved = regions.save(region)
        audit.record(NetworkAuditEntityType.REGION, id, NetworkAuditAction.UPDATED, actorUserId, before, RegionResponse.from(saved))
        return RegionResponse.from(saved)
    }

    @Transactional
    fun updateStatus(id: UUID, request: UpdateRegionStatusRequest, actorUserId: UUID): RegionResponse {
        val region = find(id)
        if (!request.active && serviceAreas.existsByRegionIdAndStatus(id, com.pavex.network.entity.ServiceAreaStatus.ACTIVE)) {
            throw ConflictException("region_in_use", "Deactivate active service areas before disabling this region.")
        }
        val before = RegionResponse.from(region)
        region.setActive(request.active)
        val saved = regions.save(region)
        audit.record(
            NetworkAuditEntityType.REGION,
            id,
            if (request.active) NetworkAuditAction.ACTIVATED else NetworkAuditAction.DEACTIVATED,
            actorUserId,
            before,
            RegionResponse.from(saved),
            request.reason,
        )
        return RegionResponse.from(saved)
    }

    fun find(id: UUID): NetworkRegion = regions.findById(id).orElseThrow {
        NotFoundException("region_not_found", "Network region was not found.")
    }
}
