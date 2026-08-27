package com.pavex.identity.service

import com.pavex.identity.entity.OutboxMessage
import com.pavex.identity.messaging.IdentityMessagingTopology
import com.pavex.identity.messaging.OutboxPayloadCipher
import com.pavex.identity.repository.OutboxMessageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

@Service
class OutboxService(

    private val repository:
        OutboxMessageRepository,

    private val jsonMapper:
        JsonMapper,

    private val payloadCipher:
        OutboxPayloadCipher,
) {

    fun enqueue(
        eventType: String,
        payload: Any,
        aggregateType: String? = null,
        aggregateId: UUID? = null,
        eventVersion: Int =
            IdentityMessagingTopology.EVENT_VERSION,
        occurredAt: Instant = Instant.now(),
    ): UUID {
        check(
            TransactionSynchronizationManager
                .isActualTransactionActive()
        ) {
            "Outbox messages must be created inside the business transaction"
        }

        val normalizedEventType =
            eventType.trim()

        require(normalizedEventType.isNotBlank()) {
            "eventType must not be blank"
        }

        require(normalizedEventType.length <= 160) {
            "eventType must not exceed 160 characters"
        }

        require(eventVersion > 0) {
            "eventVersion must be greater than zero"
        }

        val plaintext =
            jsonMapper.writeValueAsString(
                payload
            )

        val ciphertext =
            payloadCipher.encrypt(
                plaintext = plaintext,
                eventType = normalizedEventType,
                eventVersion = eventVersion,
            )

        val message =
            OutboxMessage(
                aggregateType =
                    aggregateType
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.take(80),
                aggregateId = aggregateId,
                eventType =
                    normalizedEventType,
                eventVersion = eventVersion,
                routingKey =
                    normalizedEventType,
                payloadCiphertext = ciphertext,
                nextAttemptAt = occurredAt,
                occurredAt = occurredAt,
                createdAt = occurredAt,
            )

        repository.save(message)

        return message.id
    }
}
