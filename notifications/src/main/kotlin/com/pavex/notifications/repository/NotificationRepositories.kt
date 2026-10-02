package com.pavex.notifications.repository

import com.pavex.notifications.entity.KnownShipment
import com.pavex.notifications.entity.NotificationMessage
import com.pavex.notifications.entity.UserNotification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface UserNotificationRepository : JpaRepository<UserNotification, UUID> {
    fun findByIdAndRecipientUserId(id: UUID, recipientUserId: UUID): UserNotification?

    fun existsByRecipientUserIdAndDeduplicationKey(recipientUserId: UUID, deduplicationKey: String): Boolean

    fun countByRecipientUserIdAndReadAtIsNull(recipientUserId: UUID): Int

    /**
     * Newest first, strictly after the (createdAt, id) cursor; callers pass a cursor beyond every
     * row for the first page. [filter] is "all", "read" or "unread".
     */
    @Query(
        """
        select n from UserNotification n
        where n.recipientUserId = :userId
          and (:filter = 'all' or (:filter = 'read' and n.readAt is not null) or (:filter = 'unread' and n.readAt is null))
          and (n.createdAt < :cursorAt or (n.createdAt = :cursorAt and n.id < :cursorId))
        order by n.createdAt desc, n.id desc
        """,
    )
    fun page(
        @Param("userId") userId: UUID,
        @Param("filter") filter: String,
        @Param("cursorAt") cursorAt: Instant,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable,
    ): List<UserNotification>

    @Modifying
    @Query("update UserNotification n set n.readAt = :now where n.recipientUserId = :userId and n.readAt is null")
    fun markAllRead(@Param("userId") userId: UUID, @Param("now") now: Instant): Int
}

interface NotificationMessageRepository : JpaRepository<NotificationMessage, UUID> {
    fun findAllBySenderUserIdOrderByCreatedAtDesc(senderUserId: UUID, pageable: Pageable): List<NotificationMessage>
}

interface KnownShipmentRepository : JpaRepository<KnownShipment, UUID> {
    fun findByTrackingCode(trackingCode: String): KnownShipment?
}
