package com.pavex.shipment.controller

import com.pavex.shipment.dto.PublicTrackingResponse
import com.pavex.shipment.service.PublicTrackingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tracking")
class PublicTrackingController(
    private val tracking: PublicTrackingService,
) {
    @GetMapping("/{code}")
    fun get(@PathVariable code: String): ResponseEntity<PublicTrackingResponse> =
        tracking.get(code)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}
