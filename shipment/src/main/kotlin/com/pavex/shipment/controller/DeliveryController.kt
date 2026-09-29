package com.pavex.shipment.controller

import com.pavex.shipment.dto.CourierDeliveryResponse
import com.pavex.shipment.dto.DeliveryAttemptRecordedResponse
import com.pavex.shipment.dto.RecordDeliveryAttemptRequest
import com.pavex.shipment.service.CourierDeliveryQueryService
import com.pavex.shipment.service.RecordDeliveryAttemptService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/deliveries")
class DeliveryController(
    private val attempts: RecordDeliveryAttemptService,
    private val courierDeliveries: CourierDeliveryQueryService,
) {
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('deliveries.complete-own')")
    fun mine(@AuthenticationPrincipal jwt: Jwt): List<CourierDeliveryResponse> =
        courierDeliveries.mine(UUID.fromString(jwt.subject))

    @PostMapping("/attempts")
    @PreAuthorize("hasAuthority('deliveries.complete-own')")
    fun recordAttempt(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: RecordDeliveryAttemptRequest,
    ): ResponseEntity<DeliveryAttemptRecordedResponse> {
        val result = attempts.record(UUID.fromString(jwt.subject), request)
        val builder = ResponseEntity.ok()
        if (result.replayed) builder.header("Idempotent-Replayed", "true")
        return builder.body(result.response)
    }
}
