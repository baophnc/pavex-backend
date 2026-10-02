package com.pavex.notifications.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Names as the client portal reads them ("Shipment", "Info"), kept from the .NET module. */
@Suppress("EnumEntryName")
enum class NotificationCategory { Shipment, Operations, Account, Billing, System }

@Suppress("EnumEntryName")
enum class NotificationSeverity { Info, Success, Warning, Critical }

@Entity
@Table(name = "user_notifications", schema = "notifications")
class UserNotification(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "recipient_user_id", nullable = false)
    var recipientUserId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 100)
    var code: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var category: NotificationCategory = NotificationCategory.System,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var severity: NotificationSeverity = NotificationSeverity.Info,
    @Column(nullable = false, length = 160)
    var title: String = "",
    @Column(nullable = false, length = 1000)
    var body: String = "",
    @Column(name = "action_url", length = 500)
    var actionUrl: String? = null,
    @Column(name = "subject_type", length = 80)
    var subjectType: String? = null,
    @Column(name = "subject_id", length = 100)
    var subjectId: String? = null,
    @Column(name = "deduplication_key", length = 160)
    var deduplicationKey: String? = null,
    @Column(name = "sender_user_id")
    var senderUserId: UUID? = null,
    @Column(name = "message_id")
    var messageId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "read_at")
    var readAt: Instant? = null,
) {
    val isRead: Boolean get() = readAt != null
}

@Entity
@Table(name = "messages", schema = "notifications")
class NotificationMessage(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "sender_user_id", nullable = false)
    var senderUserId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var audience: MessageAudience = MessageAudience.HUB_STAFF,
    @Column(name = "hub_id")
    var hubId: UUID? = null,
    @Column(name = "tracking_code", length = 40)
    var trackingCode: String? = null,
    @Column(nullable = false, length = 160)
    var title: String = "",
    @Column(nullable = false, length = 1000)
    var body: String = "",
    @Column(name = "recipient_count", nullable = false)
    var recipientCount: Int = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)

/** Who a message goes to. Hub audiences are the active people of one hub with those kinds. */
enum class MessageAudience(val kinds: Set<String>) {
    HUB_STAFF(setOf("WAREHOUSE_OPERATOR")),
    HUB_COURIERS(setOf("COURIER")),
    HUB_DRIVERS(setOf("LINE_HAUL_DRIVER")),
    HUB_EVERYONE(setOf("HUB_MANAGER", "WAREHOUSE_OPERATOR", "COURIER", "LINE_HAUL_DRIVER")),
    SHIPMENT_SHOP(emptySet()),
}

@Entity
@Table(name = "shipments", schema = "notifications")
class KnownShipment(
    @Id
    @Column(name = "shipment_id")
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "tracking_code", nullable = false, length = 40)
    var trackingCode: String = "",
    @Column(name = "created_by_user_id", nullable = false)
    var createdByUserId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 30)
    var channel: String = "",
    @Column(name = "origin_hub_id")
    var originHubId: UUID? = null,
    @Column(name = "destination_hub_id")
    var destinationHubId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
) {
    /** Walk-in shipments are created by counter staff, who do not want their own status mail. */
    val notifiesCreator: Boolean get() = channel != "COUNTER"
}
