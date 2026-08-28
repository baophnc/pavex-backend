package com.pavex.auth.job

import com.pavex.auth.service.OutboxBatchPublisher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "auth.outbox",
    name = ["publisher-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class OutboxPublisherJob(

    private val publisher:
        OutboxBatchPublisher,
) {

    private val logger =
        LoggerFactory.getLogger(
            OutboxPublisherJob::class.java
        )

    @Scheduled(
        fixedDelayString =
            "\${auth.outbox.poll-delay-ms:2000}",
    )
    fun publish() {
        try {
            publisher.publishNextBatch()
        } catch (exception: Exception) {
            logger.error(
                "Outbox publisher batch failed",
                exception,
            )
        }
    }
}
