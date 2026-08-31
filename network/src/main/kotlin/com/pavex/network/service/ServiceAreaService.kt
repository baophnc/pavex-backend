package com.pavex.network.service

import com.pavex.common.api.OneBasedPagination
import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.servicearea.CreateServiceAreaRequest
import com.pavex.network.dto.servicearea.ServiceAreaResponse
import com.pavex.network.dto.servicearea.UpdateServiceAreaRequest
import com.pavex.network.dto.servicearea.UpdateServiceAreaStatusRequest
import com.pavex.network.dto.toResponse
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.NetworkRegionStatus
import com.pavex.network.entity.ServiceArea
import com.pavex.network.entity.ServiceAreaStatus
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.ServiceAreaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ServiceAreaService(
    private val serviceAreas: ServiceAreaRepository,
    private val regionService: NetworkRegionService,
    private val hubService: HubService,
    private val audit: NetworkAuditService,
) {
    @Transactional(readOnly = true)
    fun list(status: ServiceAreaStatus?, regionId: UUID?, search: String?, page: Int, pageSize: Int): PageResponse<ServiceAreaResponse> {
        val pagination = OneBasedPagination.normalize(page, pageSize)
        return serviceAreas.search(
            status,
            regionId,
            search?.trim().orEmpty(),
            PageRequest.of(pagination.zeroBasedPage, pagination.pageSize, Sort.by(Sort.Direction.ASC, "code")),
        ).toResponse(ServiceAreaResponse::from)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ServiceAreaResponse = ServiceAreaResponse.from(find(id))

    @Transactional
    fun create(request: CreateServiceAreaRequest, actorUserId: UUID): ServiceAreaResponse {
        val code = request.code.trim().uppercase()
        if (serviceAreas.existsByCodeIgnoreCase(code)) {
            throw ConflictException("service_area_code_exists", "Service area code already exists.")
        }
        validateReferences(request.regionId, request.primaryHubId)
        val area = serviceAreas.save(
            ServiceArea(code = code, name = request.name.trim(), regionId = request.regionId, primaryHubId = request.primaryHubId),
        )
        audit.record(NetworkAuditEntityType.SERVICE_AREA, area.id, NetworkAuditAction.CREATED, actorUserId, after = ServiceAreaResponse.from(area))
        return ServiceAreaResponse.from(area)
    }

    @Transactional
    fun update(id: UUID, request: UpdateServiceAreaRequest, actorUserId: UUID): ServiceAreaResponse {
        val area = find(id)
        val before = ServiceAreaResponse.from(area)
        validateReferences(request.regionId, request.primaryHubId)
        area.update(request.name, request.regionId, request.primaryHubId)
        val saved = serviceAreas.save(area)
        audit.record(NetworkAuditEntityType.SERVICE_AREA, id, NetworkAuditAction.UPDATED, actorUserId, before, ServiceAreaResponse.from(saved))
        return ServiceAreaResponse.from(saved)
    }

    @Transactional
    fun updateStatus(id: UUID, request: UpdateServiceAreaStatusRequest, actorUserId: UUID): ServiceAreaResponse {
        val area = find(id)
        if (request.active) validateReferences(area.regionId, area.primaryHubId)
        val before = ServiceAreaResponse.from(area)
        area.setActive(request.active)
        val saved = serviceAreas.save(area)
        audit.record(
            NetworkAuditEntityType.SERVICE_AREA,
            id,
            if (request.active) NetworkAuditAction.ACTIVATED else NetworkAuditAction.DEACTIVATED,
            actorUserId,
            before,
            ServiceAreaResponse.from(saved),
            request.reason,
        )
        return ServiceAreaResponse.from(saved)
    }

    private fun validateReferences(regionId: UUID, primaryHubId: UUID) {
        val region = regionService.find(regionId)
        if (region.status != NetworkRegionStatus.ACTIVE) {
            throw InvalidNetworkRequestException("region_inactive", "Service area requires an active network region.")
        }
        val hub = hubService.find(primaryHubId)
        if (hub.status != HubStatus.ACTIVE) {
            throw InvalidNetworkRequestException("hub_inactive", "Service area requires an active primary hub.")
        }
    }

    fun find(id: UUID): ServiceArea = serviceAreas.findById(id).orElseThrow {
        NotFoundException("service_area_not_found", "Service area was not found.")
    }
}
