package com.pavex.partners.service

import com.pavex.contracts.identity.MerchantRoleSyncRequest
import com.pavex.partners.repository.MerchantRoleOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class MerchantRoleOutboxProcessor(
    private val messages: MerchantRoleOutboxRepository,
    private val identityClient: IdentityMerchantGateway,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${partners.role-sync.poll-delay:5000}")
    @Transactional
    fun dispatchPending() {
        val now = Instant.now()
        messages.findTop20ByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByOccurredAtAsc(now)
            .forEach { message ->
                try {
                    identityClient.synchronizeMerchantRole(
                        message.userId,
                        MerchantRoleSyncRequest(message.enabled, message.assignedByUserId),
                    )
                    message.processedAt = Instant.now()
                    message.lastError = null
                } catch (exception: RuntimeException) {
                    message.attemptCount += 1
                    message.lastError = (exception.message ?: exception.javaClass.simpleName).take(2000)
                    message.availableAt = now.plus(retryDelay(message.attemptCount))
                    logger.warn(
                        "Merchant role sync failed for outbox message {}; retry {} scheduled",
                        message.id,
                        message.attemptCount,
                    )
                }
                messages.save(message)
            }
    }

    private fun retryDelay(attempt: Int): Duration {
        val seconds = (5L shl (attempt - 1).coerceIn(0, 8)).coerceAtMost(1800)
        return Duration.ofSeconds(seconds)
    }
}
