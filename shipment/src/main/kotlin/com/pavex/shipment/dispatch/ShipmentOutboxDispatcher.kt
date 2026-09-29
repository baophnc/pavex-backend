package com.pavex.shipment.dispatch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ShipmentOutboxDispatcher(
    private val processor: ShipmentOutboxProcessor,
    @Value("\${shipment.outbox.enabled:true}") private val enabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${shipment.outbox.poll-interval-ms:2000}")
    fun dispatch() {
        if (!enabled) return
        try {
            val result = processor.processBatch()
            if (result.claimed > 0) {
                log.info(
                    "Shipment outbox batch: claimed={}, published={}, failed={}, deadLettered={}",
                    result.claimed, result.published, result.failed, result.deadLettered,
                )
            }
        } catch (exception: Exception) {
            log.error("Shipment outbox dispatcher cycle failed.", exception)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShipmentOutboxDispatcher::class.java)
    }
}
