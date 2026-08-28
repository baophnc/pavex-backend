package com.pavex.network.service

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.coverage.CoverageResponse
import com.pavex.network.dto.coverage.CreateCoverageRequest
import com.pavex.network.dto.coverage.UpdateCoverageRequest
import com.pavex.network.dto.coverage.UpdateCoverageStatusRequest
import com.pavex.network.dto.toResponse
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.ServiceAreaCoverage
import com.pavex.network.entity.ServiceAreaStatus
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.ServiceAreaCoverageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CoverageService(
    private val coverages: ServiceAreaCoverageRepository,
    private val serviceAreaService: ServiceAreaService,
    private val locations: LocationCatalogClient,
    private val audit: NetworkAuditService,
) {
    @Transactional(readOnly = true)
    fun list(
        serviceAreaId: UUID?,
        provinceCode: String?,
        activeOnly: Boolean,
        page: Int,
        pageSize: Int,
    ): PageResponse<CoverageResponse> = coverages.search(
        serviceAreaId,
        provinceCode?.trim().orEmpty(),
        activeOnly,
        PageRequest.of((page.coerceAtLeast(1) - 1), pageSize.coerceIn(1, 100)),
    ).toResponse(CoverageResponse::from)

    @Transactional(readOnly = true)
    fun get(id: UUID): CoverageResponse = CoverageResponse.from(find(id))

    @Transactional
    fun create(request: CreateCoverageRequest, actorUserId: UUID): CoverageResponse {
        validate(request.serviceAreaId, request.provinceCode, request.wardCode, null, true)
        val coverage = coverages.save(
            ServiceAreaCoverage(
                serviceAreaId = request.serviceAreaId,
                provinceCode = request.provinceCode.trim(),
                wardCode = request.wardCode?.trim()?.takeIf { it.isNotBlank() },
                tier = request.tier,
            ),
        )
        audit.record(NetworkAuditEntityType.COVERAGE, coverage.id, NetworkAuditAction.CREATED, actorUserId, after = CoverageResponse.from(coverage))
        return CoverageResponse.from(coverage)
    }

    @Transactional
    fun update(id: UUID, request: UpdateCoverageRequest, actorUserId: UUID): CoverageResponse {
        val coverage = find(id)
        val before = CoverageResponse.from(coverage)
        validate(request.serviceAreaId, request.provinceCode, request.wardCode, id, coverage.isActive)
        coverage.update(request.serviceAreaId, request.provinceCode, request.wardCode, request.tier)
        val saved = coverages.save(coverage)
        audit.record(NetworkAuditEntityType.COVERAGE, id, NetworkAuditAction.UPDATED, actorUserId, before, CoverageResponse.from(saved))
        return CoverageResponse.from(saved)
    }

    @Transactional
    fun updateStatus(id: UUID, request: UpdateCoverageStatusRequest, actorUserId: UUID): CoverageResponse {
        val coverage = find(id)
        if (request.active) {
            validate(coverage.serviceAreaId, coverage.provinceCode, coverage.wardCode, id, true)
        }
        val before = CoverageResponse.from(coverage)
        coverage.setActive(request.active)
        val saved = coverages.save(coverage)
        audit.record(
            NetworkAuditEntityType.COVERAGE,
            id,
            if (request.active) NetworkAuditAction.ACTIVATED else NetworkAuditAction.DEACTIVATED,
            actorUserId,
            before,
            CoverageResponse.from(saved),
            request.reason,
        )
        return CoverageResponse.from(saved)
    }

    private fun validate(
        serviceAreaId: UUID,
        provinceCode: String,
        wardCode: String?,
        excludedId: UUID?,
        requireUniqueActive: Boolean,
    ) {
        val area = serviceAreaService.find(serviceAreaId)
        if (area.status != ServiceAreaStatus.ACTIVE) {
            throw InvalidNetworkRequestException("service_area_inactive", "Coverage requires an active service area.")
        }
        val province = provinceCode.trim()
        val ward = wardCode?.trim()?.takeIf { it.isNotBlank() }
        if (ward == null) locations.validateProvince(province) else locations.validate(province, ward)
        if (!requireUniqueActive) return
        val conflict = if (ward == null) {
            coverages.hasActiveProvinceConflict(province, excludedId)
        } else {
            coverages.hasActiveWardConflict(ward, excludedId)
        }
        if (conflict) {
            throw ConflictException(
                "coverage_conflict",
                if (ward == null) "Province already has an active default coverage." else "Ward already has an active coverage.",
            )
        }
    }

    fun find(id: UUID): ServiceAreaCoverage = coverages.findById(id).orElseThrow {
        NotFoundException("coverage_not_found", "Service area coverage was not found.")
    }
}
