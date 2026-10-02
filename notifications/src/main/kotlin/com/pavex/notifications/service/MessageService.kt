package com.pavex.notifications.service

import com.pavex.common.security.PavexActor
import com.pavex.contracts.operations.WorkPersonSnapshot
import com.pavex.notifications.client.OperationsPeopleClient
import com.pavex.notifications.dto.HubMessageRequest
import com.pavex.notifications.dto.MessageOptionsResponse
import com.pavex.notifications.dto.SentMessageResponse
import com.pavex.notifications.dto.ShipmentMessageRequest
import com.pavex.notifications.entity.MessageAudience
import com.pavex.notifications.entity.NotificationCategory
import com.pavex.notifications.entity.NotificationMessage
import com.pavex.notifications.entity.NotificationSeverity
import com.pavex.notifications.exception.InvalidNotificationRequestException
import com.pavex.notifications.exception.NotificationForbiddenException
import com.pavex.notifications.exception.NotificationNotFoundException
import com.pavex.notifications.repository.KnownShipmentRepository
import com.pavex.notifications.repository.NotificationMessageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Who may write to whom. Head office writes to anyone at any hub; a hub manager to everyone at
 * their hubs; hub staff to the couriers and drivers of their hubs. Anyone who handles shipments
 * may write to the shop behind a shipment their hub handles.
 */
object MessageRules {
    private val MANAGER_AUDIENCES = listOf(MessageAudience.HUB_STAFF, MessageAudience.HUB_COURIERS, MessageAudience.HUB_DRIVERS, MessageAudience.HUB_EVERYONE)
    private val STAFF_AUDIENCES = listOf(MessageAudience.HUB_COURIERS, MessageAudience.HUB_DRIVERS)
    private val SHIPMENT_HANDLERS = setOf("HUB_MANAGER", "WAREHOUSE_OPERATOR", "COURIER", "LINE_HAUL_DRIVER")

    fun audiences(allHubs: Boolean, kind: String?): List<MessageAudience> = when {
        allHubs || kind == "HUB_MANAGER" -> MANAGER_AUDIENCES
        kind == "WAREHOUSE_OPERATOR" -> STAFF_AUDIENCES
        else -> emptyList()
    }

    fun mayMessageShops(allHubs: Boolean, kind: String?) = allHubs || kind in SHIPMENT_HANDLERS

    fun mayWriteToHub(allHubs: Boolean, person: WorkPersonSnapshot, audience: MessageAudience, hubId: UUID) =
        audience in audiences(allHubs, person.kind) && (allHubs || hubId in person.hubIds)

    fun mayWriteAboutShipment(allHubs: Boolean, person: WorkPersonSnapshot, shipmentHubs: Set<UUID?>) =
        mayMessageShops(allHubs, person.kind) && (allHubs || shipmentHubs.filterNotNull().any(person.hubIds::contains))
}

@Service
class MessageService(
    private val people: OperationsPeopleClient,
    private val shipments: KnownShipmentRepository,
    private val messages: NotificationMessageRepository,
    private val publisher: NotificationPublisher,
    private val clock: Clock,
) {
    fun options(actor: PavexActor): MessageOptionsResponse {
        val person = people.person(actor.userId)
        return MessageOptionsResponse(
            hubIds = person.hubIds,
            allHubs = actor.seesAllHubs,
            audiences = MessageRules.audiences(actor.seesAllHubs, person.kind),
            canMessageShops = MessageRules.mayMessageShops(actor.seesAllHubs, person.kind),
        )
    }

    @Transactional
    fun sendToHub(actor: PavexActor, request: HubMessageRequest): SentMessageResponse {
        if (request.audience == MessageAudience.SHIPMENT_SHOP) {
            throw InvalidNotificationRequestException("invalid_audience", "Use the shipment message to write to a shop.")
        }
        val person = people.person(actor.userId)
        if (!MessageRules.mayWriteToHub(actor.seesAllHubs, person, request.audience, request.hubId)) {
            throw NotificationForbiddenException("message_not_allowed", "You may not send this message to that hub.")
        }
        val recipients = people.hubPeople(request.hubId, request.audience.kinds).map { it.userId }.toSet() - actor.userId
        if (recipients.isEmpty()) {
            throw InvalidNotificationRequestException("no_recipients", "Nobody at this hub matches the chosen group.")
        }
        val message = messages.save(
            NotificationMessage(
                senderUserId = actor.userId,
                audience = request.audience,
                hubId = request.hubId,
                title = request.title.trim(),
                body = request.body.trim(),
                recipientCount = recipients.size,
                createdAt = clock.instant(),
            ),
        )
        recipients.forEach { recipient ->
            publisher.publish(
                PublishNotification(
                    recipientUserId = recipient,
                    code = "message.hub",
                    category = NotificationCategory.Operations,
                    severity = NotificationSeverity.Info,
                    title = message.title,
                    body = message.body,
                    actionUrl = "/notifications",
                    subjectType = "message",
                    subjectId = message.id.toString(),
                    deduplicationKey = "message:${message.id}",
                    senderUserId = actor.userId,
                    messageId = message.id,
                ),
            )
        }
        return SentMessageResponse.from(message)
    }

    @Transactional
    fun sendAboutShipment(actor: PavexActor, request: ShipmentMessageRequest): SentMessageResponse {
        val code = request.trackingCode.trim().uppercase()
        val shipment = shipments.findByTrackingCode(code)
            ?: throw NotificationNotFoundException("shipment_unknown", "No shipment with this tracking code.")
        if (!shipment.notifiesCreator) {
            throw InvalidNotificationRequestException(
                "sender_has_no_account",
                "This shipment was booked at a counter; the sender has no account. Call them instead.",
            )
        }
        val person = people.person(actor.userId)
        if (!MessageRules.mayWriteAboutShipment(actor.seesAllHubs, person, setOf(shipment.originHubId, shipment.destinationHubId))) {
            throw NotificationForbiddenException("message_not_allowed", "You may only write about shipments your hub handles.")
        }
        val message = messages.save(
            NotificationMessage(
                senderUserId = actor.userId,
                audience = MessageAudience.SHIPMENT_SHOP,
                trackingCode = code,
                title = "Tin nhắn về vận đơn $code",
                body = request.body.trim(),
                recipientCount = 1,
                createdAt = clock.instant(),
            ),
        )
        publisher.publish(
            PublishNotification(
                recipientUserId = shipment.createdByUserId,
                code = "message.shipment",
                category = NotificationCategory.Shipment,
                severity = NotificationSeverity.Info,
                title = message.title,
                body = message.body,
                actionUrl = "/shipments/$code",
                subjectType = "shipment",
                subjectId = code,
                deduplicationKey = "message:${message.id}",
                senderUserId = actor.userId,
                messageId = message.id,
            ),
        )
        return SentMessageResponse.from(message)
    }

    @Transactional(readOnly = true)
    fun sent(actor: PavexActor, limit: Int?): List<SentMessageResponse> =
        messages.findAllBySenderUserIdOrderByCreatedAtDesc(actor.userId, PageRequest.of(0, (limit ?: 30).coerceIn(1, 100)))
            .map(SentMessageResponse::from)
}
