package com.pavex.operations.dispatch

import com.pavex.operations.dto.OperationsOutboxDeadLetterPage
import com.pavex.operations.dto.OperationsOutboxDeadLetterResponse
import com.pavex.operations.dto.OperationsOutboxSummaryResponse
import com.pavex.operations.entity.OperationsOutboxMessage
import com.pavex.operations.exception.InvalidOperationsRequestException
import com.pavex.operations.exception.OperationsConflictException
import com.pavex.operations.exception.OperationsNotFoundException
import com.pavex.operations.repository.OperationsOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class OperationsOutboxAdminService(
    private val outbox: OperationsOutboxRepository,
    private val telemetry: OperationsTelemetry,
) {
    @Transactional(readOnly = true)
    fun summary(): OperationsOutboxSummaryResponse {
        val now = Instant.now()
        val oldest = outbox
            .findFirstByProcessedAtIsNullAndDeadLetteredAtIsNullAndDiscardedAtIsNullOrderByOccurredAtAscIdAsc()
            ?.occurredAt
        return OperationsOutboxSummaryResponse(
            pendingCount = outbox.countByProcessedAtIsNullAndDeadLetteredAtIsNullAndDiscardedAtIsNull(),
            deadLetteredCount = outbox.countByDeadLetteredAtIsNotNullAndDiscardedAtIsNull(),
            discardedCount = outbox.countByDiscardedAtIsNotNull(),
            oldestPendingOccurredAt = oldest,
            oldestPendingAgeSeconds = oldest?.let { Duration.between(it, now).seconds.coerceAtLeast(0) },
        )
    }

    @Transactional(readOnly = true)
    fun deadLetters(page: Int, pageSize: Int): OperationsOutboxDeadLetterPage {
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val result = outbox.findAllByDeadLetteredAtIsNotNullAndDiscardedAtIsNull(
            PageRequest.of(
                normalizedPage - 1,
                normalizedPageSize,
                Sort.by(Sort.Order.desc("deadLetteredAt"), Sort.Order.desc("id")),
            ),
        )
        return OperationsOutboxDeadLetterPage(
            items = result.content.map(::deadLetterResponse),
            page = normalizedPage,
            pageSize = normalizedPageSize,
            total = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun deadLetter(messageId: UUID): OperationsOutboxDeadLetterResponse {
        val message = outbox.findById(messageId).orElseThrow {
            OperationsNotFoundException("outbox_message_not_found", "The operations outbox message was not found.")
        }
        ensureActiveDeadLetter(message)
        return deadLetterResponse(message)
    }

    @Transactional
    fun retry(actorUserId: UUID, messageId: UUID) {
        requireActor(actorUserId)
        val message = outbox.findByIdForUpdate(messageId)
            ?: throw OperationsNotFoundException("outbox_message_not_found", "The operations outbox message was not found.")
        ensureActiveDeadLetter(message)
        message.deadLetteredAt = null
        message.availableAt = Instant.now()
        message.attemptCount = 0
        message.lockId = null
        message.lockedUntil = null
        outbox.saveAndFlush(message)
        telemetry.recordManualRetry()
        log.info("Operations outbox dead-letter {} was requeued by user {}.", messageId, actorUserId)
    }

    @Transactional
    fun discard(actorUserId: UUID, messageId: UUID, reason: String) {
        requireActor(actorUserId)
        val normalizedReason = reason.trim()
        if (normalizedReason.isEmpty() || normalizedReason.length > 500) {
            throw InvalidOperationsRequestException(
                "invalid_outbox_operation",
                "Discard reason is required and must not exceed 500 characters.",
            )
        }
        val message = outbox.findByIdForUpdate(messageId)
            ?: throw OperationsNotFoundException("outbox_message_not_found", "The operations outbox message was not found.")
        ensureActiveDeadLetter(message)
        message.discardedAt = Instant.now()
        message.discardedByUserId = actorUserId
        message.discardReason = normalizedReason
        message.lockId = null
        message.lockedUntil = null
        outbox.saveAndFlush(message)
        telemetry.recordManualDiscard()
        log.warn("Operations outbox dead-letter {} was discarded by user {}. Reason: {}", messageId, actorUserId, normalizedReason)
    }

    private fun ensureActiveDeadLetter(message: OperationsOutboxMessage) {
        if (message.processedAt != null || message.deadLetteredAt == null || message.discardedAt != null) {
            throw OperationsConflictException(
                "outbox_message_not_dead_lettered",
                "The operations outbox message is not an active dead-letter.",
            )
        }
    }

    private fun requireActor(actorUserId: UUID) {
        if (actorUserId == UUID(0, 0)) {
            throw InvalidOperationsRequestException("invalid_outbox_operation", "A valid actor user id is required.")
        }
    }

    private fun deadLetterResponse(message: OperationsOutboxMessage): OperationsOutboxDeadLetterResponse {
        val deadLetteredAt = message.deadLetteredAt
        if (deadLetteredAt == null) {
            throw OperationsConflictException(
                "outbox_message_not_dead_lettered",
                "The operations outbox message is not an active dead-letter.",
            )
        }
        return OperationsOutboxDeadLetterResponse(
            messageId = message.id,
            eventType = message.eventType,
            aggregateType = message.aggregateType,
            aggregateId = message.aggregateId,
            payload = message.payload,
            occurredAt = message.occurredAt,
            deadLetteredAt = deadLetteredAt,
            attemptCount = message.attemptCount,
            lastError = message.lastError,
        )
    }

    companion object {
        private const val MAX_PAGE_SIZE = 200
        private val log = LoggerFactory.getLogger(OperationsOutboxAdminService::class.java)
    }
}
