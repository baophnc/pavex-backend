package com.pavex.contracts.network

import java.util.UUID

data class NetworkLocationResolutionRequest(
    val provinceCode: String,
    val wardCode: String,
)

data class NetworkLocationResolutionResponse(
    val serviceAreaId: UUID,
    val regionId: UUID,
    val primaryHubId: UUID,
    val coverageTier: NetworkCoverageTier,
    val matchedBy: NetworkCoverageMatch,
)

enum class NetworkCoverageTier { STANDARD, REMOTE, ISLAND }
enum class NetworkCoverageMatch { WARD, PROVINCE }

/** The hub that serves a ward: where its parcels are picked up and its customers are handled. */
data class NetworkServingHubResponse(val hubId: UUID)
