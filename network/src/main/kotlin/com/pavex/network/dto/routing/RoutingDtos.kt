package com.pavex.network.dto.routing

import com.pavex.network.entity.CoverageTier
import java.util.UUID

data class ServiceAreaResolutionResponse(
    val serviceAreaId: UUID,
    val serviceAreaCode: String,
    val serviceAreaName: String,
    val regionId: UUID,
    val regionCode: String,
    val regionName: String,
    val primaryHubId: UUID,
    val primaryHubCode: String,
    val primaryHubName: String,
    val coverageId: UUID,
    val coverageTier: CoverageTier,
    val matchedBy: CoverageMatch,
)

enum class CoverageMatch { WARD, PROVINCE }
