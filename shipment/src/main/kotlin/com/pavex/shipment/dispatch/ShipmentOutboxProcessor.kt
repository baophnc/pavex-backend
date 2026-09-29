package com.pavex.shipment.dispatch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.pow

@Service
class ShipmentOutboxProcessor(
    private val store: ShipmentOutboxStore,
    private val publisher: RabbitShipmentEventPublisher,
    @Value("\${shipment.outbox.batch-size:50}") private val batchSize: Int,
    @Value("\${shipment.outbox.lock-duration:PT30S}") private val lockDuration: Duration,
    @Value("\${shipment.outbox.base-retry-delay:PT5S}") private val baseRetryDelay: Duration,
    @Value("\${shipment.outbox.max-retry-delay:PT5M}") private val maxRetryDelay: Duration,
    @Value("\${shipment.outbox.max-attempts:8}") private val maxAttempts: Int,
) {
    fun processBatch(): ShipmentOutboxBatchResult {
        validateOptions()
        val lockId = UUID.randomUUID()
        val claimed = store.claimBatch(lockId, Instant.now(), batchSize, lockDuration)
        var published = 0
        var failed = 0
        var deadLettered = 0

        claimed.forEach { event ->
            try {
                publisher.publish(event)
                if (store.markPublished(event.id, lockId, Instant.now())) published++ else failed++
            } catch (exception: Exception) {
                failed++
                val error = "${exception::class.simpleName}: ${exception.message ?: "unknown"}".take(2000)
                if (event.attempts >= maxAttempts) {
                    if (store.markDeadLettered(event.id, lockId, error)) deadLettered++
                    log.error("Shipment outbox event {} dead-lettered after {} attempts.", event.id, event.attempts, exception)
                } else {
                    val retryAt = Instant.now().plus(retryDelay(event.attempts))
                    store.markRetry(event.id, lockId, retryAt, error)
                    log.warn(
                        "Shipment outbox event {} failed on attempt {}; retry scheduled for {}.",
                        event.id,
                        event.attempts,
                        retryAt,
                    )
                }
            }
        }
        return ShipmentOutboxBatchResult(claimed.size, published, failed, deadLettered)
    }

    private fun retryDelay(attempt: Int): Duration {
        val exponent = (attempt - 1).coerceIn(0, 20)
        val millis = baseRetryDelay.toMillis() * 2.0.pow(exponent.toDouble())
        return Duration.ofMillis(millis.coerceAtMost(maxRetryDelay.toMillis().toDouble()).toLong())
    }

    private fun validateOptions() {
        require(batchSize in 1..500)
        require(!lockDuration.isZero && !lockDuration.isNegative)
        require(!baseRetryDelay.isZero && !baseRetryDelay.isNegative)
        require(maxRetryDelay >= baseRetryDelay)
        require(maxAttempts in 1..100)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShipmentOutboxProcessor::class.java)
    }
}

data class ShipmentOutboxBatchResult(
    val claimed: Int,
    val published: Int,
    val failed: Int,
    val deadLettered: Int,
)
