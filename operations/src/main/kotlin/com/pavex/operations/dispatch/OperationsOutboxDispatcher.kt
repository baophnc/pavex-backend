package com.pavex.operations.dispatch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OperationsOutboxDispatcher(
    private val processor: OperationsOutboxProcessor,
    @Value("\${operations.outbox.enabled:true}") private val enabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${operations.outbox.poll-interval-ms:2000}")
    fun dispatch() {
        if (!enabled) return
        try {
            val result = processor.processBatch()
            if (result.claimed > 0) {
                log.info(
                    "Operations outbox batch: claimed={}, processed={}, failed={}, deadLettered={}",
                    result.claimed, result.processed, result.failed, result.deadLettered,
                )
            }
        } catch (exception: Exception) {
            log.error("Operations outbox dispatcher cycle failed.", exception)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OperationsOutboxDispatcher::class.java)
    }
}
