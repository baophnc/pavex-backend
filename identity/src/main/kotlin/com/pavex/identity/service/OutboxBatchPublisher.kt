package com.pavex.identity.service

import com.pavex.identity.entity.OutboxStatus
import com.pavex.identity.repository.OutboxMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.min

@Service
class OutboxBatchPublisher(

    private val repository:
        OutboxMessageRepository,

    private val transport:
        RabbitOutboxTransport,

    @Value(
        "\${identity.outbox.batch-size:10}"
    )
    private val batchSize: Int,

    @Value(
        "\${identity.outbox.max-attempts:100}"
    )
    private val maxAttempts: Int,
) {

    private val logger =
        LoggerFactory.getLogger(
            OutboxBatchPublisher::class.java
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

        messages.forEach { message ->
            try {
                transport.publish(message)

                message.status =
                    OutboxStatus.PUBLISHED

                message.publishedAt = now
                message.lastError = null
            } catch (exception: Exception) {
                val attempts =
                    message.attempts + 1

                message.attempts = attempts

                message.lastError =
                    exception
                        .message
                        ?.replace('\n', ' ')
                        ?.replace('\r', ' ')
                        ?.take(1000)
                        ?: exception.javaClass.simpleName

                if (
                    attempts >=
                    maxAttempts.coerceAtLeast(1)
                ) {
                    message.status =
                        OutboxStatus.DEAD

                    logger.error(
                        "Outbox message {} moved to DEAD after {} attempts; eventType={}",
                        message.id,
                        attempts,
                        message.eventType,
                    )
                } else {
                    message.nextAttemptAt =
                        now.plusSeconds(
                            retryDelaySeconds(
                                attempts
                            )
                        )

                    logger.warn(
                        "Outbox publish failed messageId={} eventType={} attempt={}; retryAt={}",
                        message.id,
                        message.eventType,
                        attempts,
                        message.nextAttemptAt,
                    )
                }
            }
        }

        return messages.size
    }

    private fun retryDelaySeconds(
        attempts: Int,
    ): Long {
        val shift =
            min(
                attempts.coerceAtLeast(1),
                8,
            )

        return min(
            1L shl shift,
            300L,
        )
    }
}
