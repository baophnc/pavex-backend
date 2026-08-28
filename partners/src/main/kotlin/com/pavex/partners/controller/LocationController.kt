package com.pavex.partners.controller

import com.pavex.partners.dto.ProvinceResponse
import com.pavex.partners.dto.WardResponse
import com.pavex.partners.service.LocationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/locations")
class LocationController(
    private val locations: LocationService,
) {
    @GetMapping("/provinces")
    fun provinces(): List<ProvinceResponse> = locations.getProvinces()

    @GetMapping("/provinces/{provinceCode}/wards")
    fun wards(@PathVariable provinceCode: String): List<WardResponse> = locations.getWards(provinceCode)

    @GetMapping("/wards/{wardCode}")
    fun ward(@PathVariable wardCode: String): WardResponse = locations.getWard(wardCode)
}
