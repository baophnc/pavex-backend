package com.pavex.network.controller

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.hub.PublicHubResponse
import com.pavex.network.service.HubService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/network")
class PublicNetworkController(private val hubs: HubService) {
    @GetMapping("/hubs")
    fun hubs(
        @RequestParam(required = false) provinceCode: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "24") pageSize: Int,
    ): PageResponse<PublicHubResponse> = hubs.listPublic(provinceCode, search, page, pageSize)
}
