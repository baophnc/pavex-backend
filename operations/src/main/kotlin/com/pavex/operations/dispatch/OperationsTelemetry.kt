package com.pavex.operations.dispatch

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class OperationsTelemetry(registry: MeterRegistry) {
    private val outboxProcessed = counter(registry, "pavex.operations.outbox.processed", "Outbox messages confirmed by the broker")
    private val outboxPublishFailures = counter(registry, "pavex.operations.outbox.publish_failures", "Outbox publish attempts that failed")
    private val outboxDeadLettered = counter(registry, "pavex.operations.outbox.dead_lettered", "Outbox messages moved to dead-letter state")
    private val outboxManualRetries = counter(registry, "pavex.operations.outbox.manual_retries", "Dead-letter messages manually retried")
    private val outboxManualDiscards = counter(registry, "pavex.operations.outbox.manual_discards", "Dead-letter messages manually discarded")
    private val redispatchAssigned = counter(registry, "pavex.operations.redispatch.assigned", "Assignments successfully redispatched")
    private val redispatchDeferred = counter(registry, "pavex.operations.redispatch.deferred", "Assignments considered but not redispatched")

    fun recordOutboxBatch(result: OperationsOutboxBatchResult) {
        if (result.processed > 0) outboxProcessed.increment(result.processed.toDouble())
        if (result.failed > 0) outboxPublishFailures.increment(result.failed.toDouble())
        if (result.deadLettered > 0) outboxDeadLettered.increment(result.deadLettered.toDouble())
    }

    fun recordManualRetry() = outboxManualRetries.increment()
    fun recordManualDiscard() = outboxManualDiscards.increment()

    fun recordRedispatch(assigned: Int, deferred: Int) {
        if (assigned > 0) redispatchAssigned.increment(assigned.toDouble())
        if (deferred > 0) redispatchDeferred.increment(deferred.toDouble())
    }

    private fun counter(registry: MeterRegistry, name: String, description: String): Counter =
        Counter.builder(name).description(description).register(registry)
}
