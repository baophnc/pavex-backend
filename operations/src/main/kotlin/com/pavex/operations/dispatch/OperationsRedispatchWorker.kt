package com.pavex.operations.dispatch

import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.repository.OperationalAssignmentRepository
import com.pavex.operations.service.OperationalAssignmentService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OperationsRedispatchWorker(
    private val assignments: OperationalAssignmentRepository,
    private val service: OperationalAssignmentService,
    @Value("\${operations.redispatch.enabled:true}") private val enabled: Boolean,
    @Value("\${operations.redispatch.batch-size:25}") private val batchSize: Int,
) {
    @Scheduled(fixedDelayString = "\${operations.redispatch.poll-interval-ms:5000}")
    fun redispatch() {
        if (!enabled) return
        try {
            require(batchSize in 1..500) { "Operations redispatch batch size must be between 1 and 500." }
            val ids = assignments.findDispatchableIds(DISPATCHABLE, PageRequest.of(0, batchSize))
            var assigned = 0
            ids.forEach { id ->
                try {
                    if (service.redispatch(id)) assigned++
                } catch (exception: Exception) {
                    log.debug("Assignment {} changed or could not be redispatched in this cycle.", id, exception)
                }
            }
            if (ids.isNotEmpty()) {
                log.info("Operations redispatch batch: considered={}, assigned={}, deferred={}", ids.size, assigned, ids.size - assigned)
            }
        } catch (exception: Exception) {
            log.error("Operations redispatch cycle failed.", exception)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OperationsRedispatchWorker::class.java)
        private val DISPATCHABLE = listOf(OperationalAssignmentStatus.PENDING, OperationalAssignmentStatus.REJECTED)
    }
}
