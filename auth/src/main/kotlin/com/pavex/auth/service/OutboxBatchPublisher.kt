package com.pavex.auth.service

import com.pavex.auth.entity.OutboxMessage
import com.pavex.auth.entity.OutboxStatus
import com.pavex.auth.repository.OutboxMessageRepository
import com.pavex.outbox.OutboxRelay
import com.pavex.outbox.OutboxRelayStore
import com.pavex.outbox.RetryBackoff
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Publishes due outbox messages while holding their row locks (SKIP LOCKED) for the whole
 * transaction. `attempts` counts failed publishes; a batch is stamped with its start time.
 */
@Service
class OutboxBatchPublisher(

    private val repository:
        OutboxMessageRepository,

    private val transport:
        RabbitOutboxTransport,

    @Value(
        "\${auth.outbox.batch-size:10}"
    )
    private val batchSize: Int,

    @Value(
        "\${auth.outbox.max-attempts:100}"
    )
    maxAttempts: Int,
) {

    private val logger =
        LoggerFactory.getLogger(
            OutboxBatchPublisher::class.java
        )

    private val relay =
        OutboxRelay<OutboxMessage>(
            label = "Outbox message",
            log = logger,
            // 2 s after the first failure, doubling up to 256 s (never above 300 s).
            backoff = RetryBackoff(Duration.ofSeconds(2), Duration.ofSeconds(300), maxDoublings = 7),
            maxAttempts = maxAttempts.coerceAtLeast(1),
            idOf = { "${it.id} (eventType=${it.eventType})" },
            attemptOf = { it.attempts + 1 },
            describeError = { exception ->
                exception
                    .message
                    ?.replace('\n', ' ')
                    ?.replace('\r', ' ')
                    ?.take(1000)
                    ?: exception.javaClass.simpleName
            },
        )

    @Transactional
    fun publishNextBatch(): Int {
        val now = Instant.now()

        val messages =
            repository.lockPendingBatch(
                now = now,
                batchSize =
                    batchSize.coerceIn(
                        1,
                        100,
                    ),
            )

        relay.relay(messages, LockedRows, { now }) { transport.publish(it) }

        return messages.size
    }

    /** The rows are locked JPA entities; the transaction writes the changes. */
    private object LockedRows : OutboxRelayStore<OutboxMessage> {
        override fun markPublished(message: OutboxMessage, at: Instant): Boolean {
            message.status = OutboxStatus.PUBLISHED
            message.publishedAt = at
            message.lastError = null
            return true
        }

        override fun markRetry(message: OutboxMessage, nextAttemptAt: Instant, error: String): Boolean {
            message.attempts += 1
            message.lastError = error
            message.nextAttemptAt = nextAttemptAt
            return true
        }

        override fun markDeadLettered(message: OutboxMessage, at: Instant, error: String): Boolean {
            message.attempts += 1
            message.lastError = error
            message.status = OutboxStatus.DEAD
            return true
        }
    }
}
