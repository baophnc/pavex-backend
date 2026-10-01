package com.pavex.network.controller

import com.pavex.contracts.network.NetworkLocationResolutionRequest
import com.pavex.contracts.network.NetworkServingHubResponse
import com.pavex.network.service.NetworkResolverService
import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Partners files each merchant under the hub that serves its warehouse ward. */
@Hidden
@RestController
@RequestMapping("/internal/v1/network/partners")
class InternalPartnersNetworkController(
    private val resolver: NetworkResolverService,
) {
    @PostMapping("/serving-hub")
    fun servingHub(@Valid @RequestBody request: NetworkLocationResolutionRequest): NetworkServingHubResponse =
        NetworkServingHubResponse(resolver.resolve(request.provinceCode, request.wardCode).primaryHubId)
}
