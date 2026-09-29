package com.pavex.billing.controller

import com.pavex.billing.payment.PayOsWebhookOutcome
import com.pavex.billing.payment.PayOsWebhookService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/** payOS calls this without a user token; the payload signature is the authentication. */
@RestController
class PayOsWebhookController(
    private val webhooks: PayOsWebhookService,
) {
    @PostMapping("/api/v1/integrations/payos/webhook")
    fun handle(@RequestBody payload: JsonNode): ResponseEntity<Map<String, Any?>> {
        val result = webhooks.handle(payload)
        return when (result.outcome) {
            PayOsWebhookOutcome.PROCESSED, PayOsWebhookOutcome.DUPLICATE, PayOsWebhookOutcome.IGNORED ->
                ResponseEntity.ok(mapOf("code" to "00", "desc" to "success", "result" to result.code))
            PayOsWebhookOutcome.INVALID ->
                ResponseEntity.badRequest().body(mapOf("code" to result.code, "message" to result.message))
            PayOsWebhookOutcome.RETRY ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("code" to result.code, "message" to result.message))
        }
    }
}
