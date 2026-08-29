package com.pavex.network.seed

import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.HubType
import com.pavex.network.entity.NetworkRegion
import com.pavex.network.entity.NetworkRegionStatus
import com.pavex.network.entity.ServiceArea
import com.pavex.network.entity.ServiceAreaCoverage
import com.pavex.network.entity.ServiceAreaStatus
import com.pavex.network.repository.HubRepository
import com.pavex.network.repository.NetworkRegionRepository
import com.pavex.network.repository.ServiceAreaCoverageRepository
import com.pavex.network.repository.ServiceAreaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Order(100)
@ConditionalOnProperty(prefix = "network.seed.demo", name = ["enabled"], havingValue = "true")
class NetworkDemoGeographySeeder(
    private val regionRepository: NetworkRegionRepository,
    private val hubRepository: HubRepository,
    private val serviceAreaRepository: ServiceAreaRepository,
    private val coverageRepository: ServiceAreaCoverageRepository,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val coverages = NetworkDemoGeographyData.coverages()
        val regions = ensureRegions()
        val gateways = ensureGateways()
        val hubs = ensureAreaHubs(gateways)
        val serviceAreas = ensureServiceAreas(regions, hubs)
        val createdCoverageCount = ensureCoverages(coverages, serviceAreas)

        logger.info(
            "Network demo geography ready: {} regions, {} hubs, {} service areas, {} mapped commune-level units ({} new coverages)",
            regions.size,
            gateways.size + hubs.size,
            serviceAreas.size,
            coverages.size,
            createdCoverageCount,
        )
    }

    private fun ensureRegions(): Map<String, NetworkRegion> =
        listOf(
            "NORTH" to "Miền Bắc",
            "CENTRAL" to "Miền Trung",
            "SOUTH" to "Miền Nam",
        ).associate { (code, name) ->
            val region = regionRepository.findByCodeIgnoreCase(code)
                ?: regionRepository.save(
                    NetworkRegion(
                        code = code,
                        name = name,
                        status = NetworkRegionStatus.ACTIVE,
                    ),
                )
            code to region
        }.also { regionRepository.flush() }

    private fun ensureGateways(): Map<String, Hub> =
        NetworkDemoGeographyData.gateways.associate { seed ->
            val hub = hubRepository.findByCodeIgnoreCase(seed.code)
                ?: hubRepository.save(
                    Hub(
                        code = seed.code,
                        name = seed.name,
                        type = HubType.REGIONAL_GATEWAY,
                        status = HubStatus.ACTIVE,
                        addressLine = "${seed.name}, Việt Nam",
                        provinceCode = seed.provinceCode,
                        wardCode = seed.wardCode,
                        parentHubId = null,
                        publicVisible = true,
                        publicName = seed.name,
                    ),
                )
            seed.code to hub
        }.also { hubRepository.flush() }

    private fun ensureAreaHubs(gateways: Map<String, Hub>): Map<String, Hub> =
        NetworkDemoGeographyData.serviceAreas.associate { seed ->
            val parent = requireNotNull(gateways[seed.gatewayCode]) { "Missing demo gateway ${seed.gatewayCode}" }
            val hub = hubRepository.findByCodeIgnoreCase(seed.hubCode)
                ?: hubRepository.save(
                    Hub(
                        code = seed.hubCode,
                        name = seed.hubName,
                        type = HubType.LOCAL,
                        status = HubStatus.ACTIVE,
                        addressLine = "${seed.hubName}, ${provinceName(seed.provinceCode)}",
                        provinceCode = seed.provinceCode,
                        wardCode = seed.hubWardCode,
                        parentHubId = parent.id,
                        publicVisible = false,
                        publicName = null,
                    ),
                )
            seed.hubCode to hub
        }.also { hubRepository.flush() }

    private fun ensureServiceAreas(
        regions: Map<String, NetworkRegion>,
        hubs: Map<String, Hub>,
    ): Map<String, ServiceArea> =
        NetworkDemoGeographyData.serviceAreas.associate { seed ->
            val region = requireNotNull(regions[seed.regionCode]) { "Missing demo region ${seed.regionCode}" }
            val hub = requireNotNull(hubs[seed.hubCode]) { "Missing demo hub ${seed.hubCode}" }
            val area = serviceAreaRepository.findByCodeIgnoreCase(seed.code)
                ?: serviceAreaRepository.save(
                    ServiceArea(
                        code = seed.code,
                        name = seed.name,
                        regionId = region.id,
                        primaryHubId = hub.id,
                        status = ServiceAreaStatus.ACTIVE,
                    ),
                )
            seed.code to area
        }.also { serviceAreaRepository.flush() }

    private fun ensureCoverages(
        seeds: List<DemoCoverageSeed>,
        serviceAreas: Map<String, ServiceArea>,
    ): Int {
        val existingActiveCoverage = coverageRepository.findAll()
            .asSequence()
            .filter { it.isActive && it.wardCode != null }
            .associateBy { requireNotNull(it.wardCode) }
            .toMutableMap()

        val newCoverages = mutableListOf<ServiceAreaCoverage>()
        seeds.forEach { seed ->
            val existing = existingActiveCoverage[seed.wardCode]
            if (existing != null) {
                val expectedArea = requireNotNull(serviceAreas[seed.serviceAreaCode])
                if (existing.serviceAreaId != expectedArea.id) {
                    logger.warn(
                        "Demo seed keeps existing operator coverage for ward {} instead of remapping it to {}",
                        seed.wardCode,
                        seed.serviceAreaCode,
                    )
                }
                return@forEach
            }

            val serviceArea = requireNotNull(serviceAreas[seed.serviceAreaCode]) {
                "Missing demo service area ${seed.serviceAreaCode}"
            }
            val coverage = ServiceAreaCoverage(
                serviceAreaId = serviceArea.id,
                provinceCode = seed.provinceCode,
                wardCode = seed.wardCode,
                tier = seed.tier,
                isActive = true,
            )
            newCoverages += coverage
            existingActiveCoverage[seed.wardCode] = coverage
        }

        if (newCoverages.isNotEmpty()) {
            coverageRepository.saveAll(newCoverages)
            coverageRepository.flush()
        }
        return newCoverages.size
    }

    private fun provinceName(code: String): String =
        when (code) {
            "01" -> "Hà Nội"
            "48" -> "Đà Nẵng"
            "79" -> "Hồ Chí Minh"
            else -> code
        }
}
