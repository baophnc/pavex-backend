package com.pavex.operations.dispatch

import com.pavex.operations.repository.OperationalAssignmentRepository
import com.pavex.operations.service.AssignmentDispatchQueue
import com.pavex.operations.service.OperationalAssignmentService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OperationsRedispatchWorker(
    private val assignments: OperationalAssignmentRepository,
    private val service: OperationalAssignmentService,
    private val queue: AssignmentDispatchQueue,
    private val telemetry: OperationsTelemetry,
    @Value("\${operations.redispatch.enabled:true}") private val enabled: Boolean,
    @Value("\${operations.redispatch.batch-size:25}") private val batchSize: Int,
) {
    @Scheduled(fixedDelayString = "\${operations.redispatch.poll-interval-ms:5000}")
    fun redispatch() {
        if (!enabled) return
        try {
            require(batchSize in 1..500) { "Operations redispatch batch size must be between 1 and 500." }
            val released = releaseUnaccepted()
            val ids = assignments.findDispatchableIds(batchSize)
            var assigned = 0
            ids.forEach { id ->
                try {
                    if (service.redispatch(id)) assigned++
                } catch (exception: Exception) {
                    log.debug("Assignment {} changed or could not be redispatched in this cycle.", id, exception)
                }
            }
            val deferred = ids.size - assigned
            telemetry.recordRedispatch(assigned, deferred)
            // A backlog nobody can take yet is retried every cycle; only log cycles that moved work.
            if (assigned > 0 || released > 0) {
                log.info(
                    "Operations redispatch batch: released={}, considered={}, assigned={}, deferred={}",
                    released,
                    ids.size,
                    assigned,
                    deferred,
                )
            }
        } catch (exception: Exception) {
            log.error("Operations redispatch cycle failed.", exception)
        }
    }

    /** Jobs nobody accepted in time go back to the queue and are offered again in this same cycle. */
    private fun releaseUnaccepted(): Int = queue.unacceptedIds(batchSize).count { id ->
        try {
            queue.releaseIfUnaccepted(id)
        } catch (exception: Exception) {
            log.debug("Assignment {} changed or could not be released in this cycle.", id, exception)
            false
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OperationsRedispatchWorker::class.java)
    }
}
