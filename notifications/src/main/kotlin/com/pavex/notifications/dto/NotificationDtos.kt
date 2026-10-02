package com.pavex.notifications.dto

import com.pavex.notifications.entity.MessageAudience
import com.pavex.notifications.entity.NotificationCategory
import com.pavex.notifications.entity.NotificationMessage
import com.pavex.notifications.entity.NotificationSeverity
import com.pavex.notifications.entity.UserNotification
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val code: String,
    val category: NotificationCategory,
    val severity: NotificationSeverity,
    val title: String,
    val body: String,
    val actionUrl: String?,
    val subjectType: String?,
    val subjectId: String?,
    val isRead: Boolean,
    val createdAt: Instant,
    val readAt: Instant?,
    /** Who wrote it, for messages people send; null for automatic notices. */
    val senderUserId: UUID?,
) {
    companion object {
        fun from(value: UserNotification) = NotificationResponse(
            value.id, value.code, value.category, value.severity, value.title, value.body, value.actionUrl,
            value.subjectType, value.subjectId, value.isRead, value.createdAt, value.readAt, value.senderUserId,
        )
    }
}

data class NotificationPageResponse(
    val items: List<NotificationResponse>,
    val nextCursor: String?,
    val unreadCount: Int,
)

data class UnreadCountResponse(val count: Int)

data class MarkAllReadResponse(val updatedCount: Int)

/** A message to the people of a hub. */
data class HubMessageRequest(
    val audience: MessageAudience,
    val hubId: UUID,
    @field:NotBlank @field:Size(max = 160) val title: String,
    @field:NotBlank @field:Size(max = 1000) val body: String,
)

/** A message to the shop that sent a shipment, about that shipment. */
data class ShipmentMessageRequest(
    @field:NotBlank @field:Size(max = 40) val trackingCode: String,
    @field:NotBlank @field:Size(max = 1000) val body: String,
)

data class SentMessageResponse(
    val id: UUID,
    val audience: MessageAudience,
    val hubId: UUID?,
    val trackingCode: String?,
    val title: String,
    val body: String,
    val recipientCount: Int,
    val createdAt: Instant,
) {
    companion object {
        fun from(value: NotificationMessage) = SentMessageResponse(
            value.id, value.audience, value.hubId, value.trackingCode, value.title, value.body, value.recipientCount, value.createdAt,
        )
    }
}

/** What the signed-in person may send, for the compose screen. */
data class MessageOptionsResponse(
    val hubIds: List<UUID>,
    val allHubs: Boolean,
    val audiences: List<MessageAudience>,
    val canMessageShops: Boolean,
)
