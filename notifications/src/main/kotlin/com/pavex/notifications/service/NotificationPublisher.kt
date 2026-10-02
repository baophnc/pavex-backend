package com.pavex.notifications.service

import com.pavex.notifications.entity.NotificationCategory
import com.pavex.notifications.entity.NotificationSeverity
import com.pavex.notifications.entity.UserNotification
import com.pavex.notifications.repository.UserNotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class PublishNotification(
    val recipientUserId: UUID,
    val code: String,
    val category: NotificationCategory,
    val severity: NotificationSeverity,
    val title: String,
    val body: String,
    val actionUrl: String? = null,
    val subjectType: String? = null,
    val subjectId: String? = null,
    /** The same key never reaches the same person twice: events are redelivered, broadcasts retried. */
    val deduplicationKey: String? = null,
    val senderUserId: UUID? = null,
    val messageId: UUID? = null,
)

/** Puts a notification in one person's inbox, once. */
@Service
class NotificationPublisher(
    private val notifications: UserNotificationRepository,
    private val clock: Clock,
) {
    /** Returns the new notification, or null when this key already reached this person. */
    @Transactional
    fun publish(message: PublishNotification): UserNotification? {
        val key = message.deduplicationKey?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
        if (key != null && notifications.existsByRecipientUserIdAndDeduplicationKey(message.recipientUserId, key)) return null
        return notifications.save(
            UserNotification(
                recipientUserId = message.recipientUserId,
                code = text(message.code, 100).lowercase(),
                category = message.category,
                severity = message.severity,
                title = text(message.title, 160),
                body = text(message.body, 1000),
                actionUrl = message.actionUrl?.trim()?.takeIf { it.startsWith("/") && !it.startsWith("//") }?.take(500),
                subjectType = message.subjectType?.take(80),
                subjectId = message.subjectId?.take(100),
                deduplicationKey = key,
                senderUserId = message.senderUserId,
                messageId = message.messageId,
                createdAt = clock.instant(),
            ),
        )
    }

    private fun text(value: String, max: Int): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Notification text is required." }
        return if (normalized.length <= max) normalized else normalized.take(max - 1) + "…"
    }
}
