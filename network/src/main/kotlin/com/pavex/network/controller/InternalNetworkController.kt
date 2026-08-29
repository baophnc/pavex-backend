package com.pavex.network.controller

import com.pavex.contracts.network.NetworkCoverageMatch
import com.pavex.contracts.network.NetworkCoverageTier
import com.pavex.contracts.network.NetworkLocationResolutionRequest
import com.pavex.contracts.network.NetworkLocationResolutionResponse
import com.pavex.network.dto.routing.CoverageMatch
import com.pavex.network.entity.CoverageTier
import com.pavex.network.service.NetworkResolverService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/v1/network")
class InternalNetworkController(
    private val resolver: NetworkResolverService,
) {
    @PostMapping("/locations/resolve")
    fun resolveForPricing(
        @Valid @RequestBody request: NetworkLocationResolutionRequest,
    ): NetworkLocationResolutionResponse {
        val resolved = resolver.resolve(request.provinceCode, request.wardCode)
        return NetworkLocationResolutionResponse(
            serviceAreaId = resolved.serviceAreaId,
            regionId = resolved.regionId,
            primaryHubId = resolved.primaryHubId,
            coverageTier = resolved.coverageTier.toContract(),
            matchedBy = resolved.matchedBy.toContract(),
        )
    }
}

private fun CoverageTier.toContract(): NetworkCoverageTier = NetworkCoverageTier.valueOf(name)
private fun CoverageMatch.toContract(): NetworkCoverageMatch = NetworkCoverageMatch.valueOf(name)
