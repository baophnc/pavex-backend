package com.pavex.network.service

import com.pavex.network.dto.routing.CoverageMatch
import com.pavex.network.dto.routing.ServiceAreaResolutionResponse
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.NetworkRegionStatus
import com.pavex.network.entity.ServiceAreaStatus
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.ServiceAreaCoverageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NetworkResolverService(
    private val coverages: ServiceAreaCoverageRepository,
    private val serviceAreas: ServiceAreaService,
    private val regions: NetworkRegionService,
    private val hubs: HubService,
    private val locations: LocationCatalogClient,
) {
    @Transactional(readOnly = true)
    fun resolve(provinceCode: String, wardCode: String): ServiceAreaResolutionResponse {
        val province = provinceCode.trim()
        val ward = wardCode.trim()
        locations.validate(province, ward)
        val wardCoverage = coverages.findFirstByWardCodeAndIsActiveTrue(ward)
        val coverage = wardCoverage
            ?: coverages.findFirstByProvinceCodeAndWardCodeIsNullAndIsActiveTrue(province)
            ?: throw NotFoundException("coverage_not_found", "No active network coverage was found for this location.")
        val area = serviceAreas.find(coverage.serviceAreaId)
        if (area.status != ServiceAreaStatus.ACTIVE) {
            throw NotFoundException("coverage_unavailable", "The matched service area is not active.")
        }
        val region = regions.find(area.regionId)
        if (region.status != NetworkRegionStatus.ACTIVE) {
            throw NotFoundException("coverage_unavailable", "The matched network region is not active.")
        }
        val hub = hubs.find(area.primaryHubId)
        if (hub.status != HubStatus.ACTIVE) {
            throw NotFoundException("coverage_unavailable", "The matched primary hub is not active.")
        }
        return ServiceAreaResolutionResponse(
            serviceAreaId = area.id,
            serviceAreaCode = area.code,
            serviceAreaName = area.name,
            regionId = region.id,
            regionCode = region.code,
            regionName = region.name,
            primaryHubId = hub.id,
            primaryHubCode = hub.code,
            primaryHubName = hub.name,
            coverageId = coverage.id,
            coverageTier = coverage.tier,
            matchedBy = if (wardCoverage != null) CoverageMatch.WARD else CoverageMatch.PROVINCE,
        )
    }
}
