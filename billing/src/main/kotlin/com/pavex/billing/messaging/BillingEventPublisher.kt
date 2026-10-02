package com.pavex.billing.messaging

import com.pavex.billing.dto.CashRemittanceResponse
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Tells others what billing did, after it is saved: today only that a courier handed cash to a hub,
 * so the hub manager is told there is money to count. Best effort: a lost event costs a notice,
 * never money.
 */
@Component
class BillingEventPublisher(
    private val rabbit: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${billing.messaging.exchange:pavex.events}") private val exchange: String,
) {
    fun remittanceSubmitted(remittance: CashRemittanceResponse) {
        val eventId = UUID.randomUUID().toString()
        val payload = mapOf(
            "remittanceId" to remittance.id,
            "remittanceNumber" to remittance.remittanceNumber,
            "courierUserId" to remittance.courierUserId,
            "hubId" to remittance.hubId,
            "amount" to remittance.declaredAmount,
            "submittedAt" to remittance.submittedAt.toString(),
        )
        try {
            rabbit.convertAndSend(exchange, REMITTANCE_SUBMITTED, objectMapper.writeValueAsBytes(payload)) { message ->
                message.messageProperties.apply {
                    messageId = eventId
                    contentType = "application/json"
                    setHeader("X-Pavex-Event-Id", eventId)
                    setHeader("X-Pavex-Event-Type", REMITTANCE_SUBMITTED)
                    setHeader("X-Pavex-Producer", "billing-service")
                }
                message
            }
        } catch (exception: RuntimeException) {
            log.warn("Could not announce remittance {}: {}", remittance.remittanceNumber, exception.message)
        }
    }

    companion object {
        const val REMITTANCE_SUBMITTED = "billing.cash.remittance-submitted"
        private val log = LoggerFactory.getLogger(BillingEventPublisher::class.java)
    }
}
