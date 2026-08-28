package com.pavex.network.controller

import com.pavex.network.dto.routing.RoutePlanResponse
import com.pavex.network.dto.routing.RoutePlanningMode
import com.pavex.network.dto.routing.ServiceAreaResolutionResponse
import com.pavex.network.service.NetworkResolverService
import com.pavex.network.service.NetworkRoutePlanner
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant

@RestController
@RequestMapping("/api/v1/admin/network/routing")
class NetworkRoutingController(
    private val resolver: NetworkResolverService,
    private val planner: NetworkRoutePlanner,
) {
    @GetMapping("/resolve")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun resolve(
        @RequestParam provinceCode: String,
        @RequestParam wardCode: String,
    ): ServiceAreaResolutionResponse = resolver.resolve(provinceCode, wardCode)

    @GetMapping("/plan")
    @PreAuthorize("hasAnyAuthority('network.read','network.manage')")
    fun plan(
        @RequestParam originProvinceCode: String,
        @RequestParam originWardCode: String,
        @RequestParam destinationProvinceCode: String,
        @RequestParam destinationWardCode: String,
        @RequestParam shipmentWeightKg: BigDecimal,
        @RequestParam(required = false) readyAt: Instant?,
        @RequestParam(defaultValue = "PREFER_TEMPLATE") mode: RoutePlanningMode,
    ): RoutePlanResponse = planner.plan(
        originProvinceCode,
        originWardCode,
        destinationProvinceCode,
        destinationWardCode,
        shipmentWeightKg,
        readyAt ?: Instant.now(),
        mode,
    )
}
