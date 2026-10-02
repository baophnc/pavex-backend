package com.pavex.notifications.service

import com.pavex.notifications.dto.NotificationPageResponse
import com.pavex.notifications.dto.NotificationResponse
import com.pavex.notifications.entity.NotificationCategory
import com.pavex.notifications.entity.NotificationSeverity
import com.pavex.notifications.exception.InvalidNotificationRequestException
import com.pavex.notifications.exception.NotificationNotFoundException
import com.pavex.notifications.repository.UserNotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** One person's inbox: newest first, with read and unread state. */
@Service
class NotificationInboxService(
    private val notifications: UserNotificationRepository,
    private val publisher: NotificationPublisher,
    private val clock: Clock,
) {
    @Transactional
    fun page(userId: UUID, status: String?, cursor: String?, limit: Int?): NotificationPageResponse {
        welcome(userId)
        val filter = when (status?.lowercase()?.ifBlank { null } ?: "all") {
            "all" -> "all"
            "read" -> "read"
            "unread" -> "unread"
            else -> throw InvalidNotificationRequestException("invalid_notification_status", "Status must be all, unread or read.")
        }
        val size = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val after = cursor?.takeIf { it.isNotBlank() }?.let(::parseCursor) ?: Cursor(FAR_FUTURE, MAX_UUID)
        val rows = notifications.page(userId, filter, after.createdAt, after.id, PageRequest.of(0, size + 1))
        val items = rows.take(size)
        val next = if (rows.size > size) items.last().let { Cursor(it.createdAt, it.id).encode() } else null
        return NotificationPageResponse(items.map(NotificationResponse::from), next, notifications.countByRecipientUserIdAndReadAtIsNull(userId))
    }

    @Transactional
    fun unreadCount(userId: UUID): Int {
        welcome(userId)
        return notifications.countByRecipientUserIdAndReadAtIsNull(userId)
    }

    @Transactional
    fun setRead(userId: UUID, notificationId: UUID, read: Boolean): NotificationResponse {
        val notification = notifications.findByIdAndRecipientUserId(notificationId, userId)
            ?: throw NotificationNotFoundException("notification_not_found", "Notification was not found.")
        notification.readAt = if (read) notification.readAt ?: clock.instant() else null
        return NotificationResponse.from(notification)
    }

    @Transactional
    fun markAllRead(userId: UUID): Int = notifications.markAllRead(userId, clock.instant())

    /** The first look at the inbox finds a greeting rather than an empty box. */
    private fun welcome(userId: UUID) {
        publisher.publish(
            PublishNotification(
                recipientUserId = userId,
                code = "account.welcome",
                category = NotificationCategory.Account,
                severity = NotificationSeverity.Info,
                title = "Chào mừng bạn đến với Pavex",
                body = "Hộp thư này báo cho bạn những thay đổi quan trọng: vận đơn, công việc và tin nhắn từ bưu cục.",
                deduplicationKey = "account.welcome",
            ),
        )
    }

    private data class Cursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("${createdAt.toEpochMilli()}:${createdAt.nano % 1_000_000}:$id".toByteArray(StandardCharsets.UTF_8))
    }

    private fun parseCursor(value: String): Cursor = try {
        val (millis, nanos, id) = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split(":")
        Cursor(Instant.ofEpochMilli(millis.toLong()).plusNanos(nanos.toLong()), UUID.fromString(id))
    } catch (_: RuntimeException) {
        throw InvalidNotificationRequestException("invalid_cursor", "Notification cursor is invalid.")
    }

    companion object {
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 100
        private val FAR_FUTURE: Instant = Instant.parse("9999-12-31T00:00:00Z")
        private val MAX_UUID = UUID(-1L, -1L)
    }
}
