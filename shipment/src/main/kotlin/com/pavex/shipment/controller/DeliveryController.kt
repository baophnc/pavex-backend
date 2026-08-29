package com.pavex.shipment.controller

import com.pavex.shipment.dto.DeliveryAttemptRecordedResponse
import com.pavex.shipment.dto.RecordDeliveryAttemptRequest
import com.pavex.shipment.service.RecordDeliveryAttemptService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/deliveries")
class DeliveryController(
    private val attempts: RecordDeliveryAttemptService,
) {
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
