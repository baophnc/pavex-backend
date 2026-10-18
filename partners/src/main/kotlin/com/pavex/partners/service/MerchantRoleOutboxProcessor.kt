package com.pavex.partners.service

import com.pavex.contracts.identity.MerchantRoleSyncRequest
import com.pavex.outbox.OutboxRelay
import com.pavex.outbox.OutboxRelayStore
import com.pavex.outbox.RetryBackoff
import com.pavex.partners.entity.MerchantRoleOutboxMessage
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

    /** A role change is retried until Identity takes it; it is never dead-lettered. */
    private val relay = OutboxRelay<MerchantRoleOutboxMessage>(
        label = "Merchant role outbox message",
        log = logger,
        backoff = BACKOFF,
        maxAttempts = null,
        idOf = { it.id },
        attemptOf = { it.attemptCount + 1 },
        describeError = { (it.message ?: it.javaClass.simpleName).take(2000) },
    )

    /**
     * Rows stay locked (FOR UPDATE SKIP LOCKED) until this transaction commits, so a
     * concurrent status change for the same user waits for the in-flight dispatch
     * instead of overtaking it.
     */
    @Scheduled(fixedDelayString = "\${partners.role-sync.poll-delay:5000}")
    @Transactional
    fun dispatchPending() {
        val now = Instant.now()
        messages.claimDispatchable(now, BATCH_SIZE).forEach { message ->
            dispatch(message, now)
            messages.save(message)
        }
    }

    private fun dispatch(message: MerchantRoleOutboxMessage, now: Instant) {
        if (messages.existsNewerRoleChange(message.userId, message.occurredAt, message.id)) {
            message.markSuperseded(now)
            logger.info(
                "Merchant role outbox message {} superseded by a newer role change for user {}",
                message.id,
                message.userId,
            )
            return
        }

        relay.deliver(message, LockedRows) {
            identityClient.synchronizeMerchantRole(
                it.userId,
                MerchantRoleSyncRequest(it.enabled, it.assignedByUserId),
            )
        }
    }

    internal fun retryDelay(attempt: Int): Duration = BACKOFF.delayAfter(attempt)

    /** The rows are locked JPA entities; [dispatchPending] saves them. */
    private object LockedRows : OutboxRelayStore<MerchantRoleOutboxMessage> {
        override fun markPublished(message: MerchantRoleOutboxMessage, at: Instant): Boolean {
            message.processedAt = at
            message.lastError = null
            return true
        }

        override fun markRetry(message: MerchantRoleOutboxMessage, nextAttemptAt: Instant, error: String): Boolean {
            message.attemptCount += 1
            message.lastError = error
            message.availableAt = nextAttemptAt
            return true
        }

        override fun markDeadLettered(message: MerchantRoleOutboxMessage, at: Instant, error: String): Boolean =
            error("Merchant role changes are retried, never dead-lettered.")
    }

    companion object {
        private const val BATCH_SIZE = 20

        /** 5 s, doubling up to 21 min 20 s (5 s × 2⁸). */
        private val BACKOFF = RetryBackoff(Duration.ofSeconds(5), Duration.ofSeconds(1800), maxDoublings = 8)
    }
}
