package com.pavex.operations.dispatch

import com.pavex.outbox.OutboxLeaseSettings
import com.pavex.outbox.OutboxRelay
import com.pavex.outbox.OutboxRelayStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Leases a batch, publishes each message to RabbitMQ and records the outcome under this batch's lease. */
@Service
class OperationsOutboxProcessor(
    private val store: OperationsOutboxStore,
    private val publisher: RabbitOperationsEventPublisher,
    private val telemetry: OperationsTelemetry,
    @Value("\${operations.outbox.batch-size:50}") batchSize: Int,
    @Value("\${operations.outbox.lock-duration:PT30S}") lockDuration: Duration,
    @Value("\${operations.outbox.base-retry-delay:PT5S}") baseRetryDelay: Duration,
    @Value("\${operations.outbox.max-retry-delay:PT5M}") maxRetryDelay: Duration,
    @Value("\${operations.outbox.max-attempts:8}") maxAttempts: Int,
) {
    private val settings = OutboxLeaseSettings(batchSize, lockDuration, baseRetryDelay, maxRetryDelay, maxAttempts)
    private val relay = OutboxRelay<OperationsOutboxDelivery>(
        label = "Operations outbox message",
        log = log,
        backoff = settings.backoff,
        maxAttempts = maxAttempts,
        idOf = { it.messageId },
        attemptOf = { it.attemptCount },
    )

    fun processBatch(): OperationsOutboxBatchResult {
        settings.validate()
        val lockId = UUID.randomUUID()
        val claimed = store.claimBatch(lockId, Instant.now(), settings.batchSize, settings.lockDuration)
        val result = relay.relay(claimed, leasedBy(lockId)) { message ->
            publisher.publish(
                OperationsIntegrationEvent(
                    message.messageId,
                    message.eventType,
                    message.aggregateType,
                    message.aggregateId,
                    message.payload,
                    message.occurredAt,
                    message.attemptCount,
                ),
            )
        }
        return OperationsOutboxBatchResult(result.claimed, result.published, result.failed, result.deadLettered)
            .also(telemetry::recordOutboxBatch)
    }

    private fun leasedBy(lockId: UUID) = object : OutboxRelayStore<OperationsOutboxDelivery> {
        override fun markPublished(message: OperationsOutboxDelivery, at: Instant) =
            store.markProcessed(message.messageId, lockId, at)

        override fun markRetry(message: OperationsOutboxDelivery, nextAttemptAt: Instant, error: String) =
            store.markFailed(message.messageId, lockId, nextAttemptAt, error)

        override fun markDeadLettered(message: OperationsOutboxDelivery, at: Instant, error: String) =
            store.markDeadLettered(message.messageId, lockId, at, error)
    }

    companion object {
        private val log = LoggerFactory.getLogger(OperationsOutboxProcessor::class.java)
    }
}

data class OperationsOutboxBatchResult(
    val claimed: Int,
    val processed: Int,
    val failed: Int,
    val deadLettered: Int,
)
