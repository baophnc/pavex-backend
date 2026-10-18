package com.pavex.shipment.dispatch

import com.pavex.outbox.OutboxBatchResult
import com.pavex.outbox.OutboxLeaseSettings
import com.pavex.outbox.OutboxRelay
import com.pavex.outbox.OutboxRelayStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Leases a batch, publishes each event to RabbitMQ and records the outcome under this batch's lease. */
@Service
class ShipmentOutboxProcessor(
    private val store: ShipmentOutboxStore,
    private val publisher: RabbitShipmentEventPublisher,
    @Value("\${shipment.outbox.batch-size:50}") batchSize: Int,
    @Value("\${shipment.outbox.lock-duration:PT30S}") lockDuration: Duration,
    @Value("\${shipment.outbox.base-retry-delay:PT5S}") baseRetryDelay: Duration,
    @Value("\${shipment.outbox.max-retry-delay:PT5M}") maxRetryDelay: Duration,
    @Value("\${shipment.outbox.max-attempts:8}") maxAttempts: Int,
) {
    private val settings = OutboxLeaseSettings(batchSize, lockDuration, baseRetryDelay, maxRetryDelay, maxAttempts)
    private val relay = OutboxRelay<ShipmentOutboxDelivery>(
        label = "Shipment outbox event",
        log = log,
        backoff = settings.backoff,
        maxAttempts = maxAttempts,
        idOf = { it.id },
        attemptOf = { it.attempts },
    )

    fun processBatch(): ShipmentOutboxBatchResult {
        settings.validate()
        val lockId = UUID.randomUUID()
        val claimed = store.claimBatch(lockId, Instant.now(), settings.batchSize, settings.lockDuration)
        return relay.relay(claimed, leasedBy(lockId)) { publisher.publish(it) }
    }

    private fun leasedBy(lockId: UUID) = object : OutboxRelayStore<ShipmentOutboxDelivery> {
        override fun markPublished(message: ShipmentOutboxDelivery, at: Instant) =
            store.markPublished(message.id, lockId, at)

        override fun markRetry(message: ShipmentOutboxDelivery, nextAttemptAt: Instant, error: String) =
            store.markRetry(message.id, lockId, nextAttemptAt, error)

        override fun markDeadLettered(message: ShipmentOutboxDelivery, at: Instant, error: String) =
            store.markDeadLettered(message.id, lockId, error)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShipmentOutboxProcessor::class.java)
    }
}

typealias ShipmentOutboxBatchResult = OutboxBatchResult
