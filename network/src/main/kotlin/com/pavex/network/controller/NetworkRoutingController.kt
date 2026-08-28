package com.pavex.network.controller

import com.pavex.network.dto.routing.ServiceAreaResolutionResponse
import com.pavex.network.service.NetworkResolverService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/network/routing")
class NetworkRoutingController(private val resolver: NetworkResolverService) {
    @GetMapping("/resolve")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun resolve(
        @RequestParam provinceCode: String,
        @RequestParam wardCode: String,
    ): ServiceAreaResolutionResponse = resolver.resolve(provinceCode, wardCode)
}
