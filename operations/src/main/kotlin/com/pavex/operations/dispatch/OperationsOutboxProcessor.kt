package com.pavex.operations.dispatch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.pow

@Service
class OperationsOutboxProcessor(
    private val store: OperationsOutboxStore,
    private val publisher: RabbitOperationsEventPublisher,
    @Value("\${operations.outbox.batch-size:50}") private val batchSize: Int,
    @Value("\${operations.outbox.lock-duration:PT30S}") private val lockDuration: Duration,
    @Value("\${operations.outbox.base-retry-delay:PT5S}") private val baseRetryDelay: Duration,
    @Value("\${operations.outbox.max-retry-delay:PT5M}") private val maxRetryDelay: Duration,
    @Value("\${operations.outbox.max-attempts:8}") private val maxAttempts: Int,
) {
    fun processBatch(): OperationsOutboxBatchResult {
        validateOptions()
        val lockId = UUID.randomUUID()
        val claimed = store.claimBatch(lockId, Instant.now(), batchSize, lockDuration)
        var processed = 0
        var failed = 0
        var deadLettered = 0

        claimed.forEach { message ->
            try {
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
                if (store.markProcessed(message.messageId, lockId, Instant.now())) processed++ else failed++
            } catch (exception: Exception) {
                failed++
                val now = Instant.now()
                val error = normalizeError(exception)
                if (message.attemptCount >= maxAttempts) {
                    if (store.markDeadLettered(message.messageId, lockId, now, error)) deadLettered++
                    log.error(
                        "Operations outbox message {} dead-lettered after {} attempts.",
                        message.messageId,
                        message.attemptCount,
                        exception,
                    )
                } else {
                    val retryAt = now.plus(retryDelay(message.attemptCount))
                    store.markFailed(message.messageId, lockId, retryAt, error)
                    log.warn(
                        "Operations outbox message {} failed on attempt {}; retry scheduled for {}.",
                        message.messageId,
                        message.attemptCount,
                        retryAt,
                    )
                }
            }
        }
        return OperationsOutboxBatchResult(claimed.size, processed, failed, deadLettered)
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

    private fun normalizeError(exception: Exception): String =
        "${exception::class.simpleName}: ${exception.message ?: "unknown"}".take(2000)

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
