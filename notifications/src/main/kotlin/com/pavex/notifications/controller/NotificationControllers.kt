package com.pavex.notifications.controller

import com.pavex.common.security.PavexActor
import com.pavex.notifications.dto.HubMessageRequest
import com.pavex.notifications.dto.MarkAllReadResponse
import com.pavex.notifications.dto.MessageOptionsResponse
import com.pavex.notifications.dto.NotificationPageResponse
import com.pavex.notifications.dto.NotificationResponse
import com.pavex.notifications.dto.SentMessageResponse
import com.pavex.notifications.dto.ShipmentMessageRequest
import com.pavex.notifications.dto.UnreadCountResponse
import com.pavex.notifications.service.MessageService
import com.pavex.notifications.service.NotificationInboxService
import jakarta.validation.Valid
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Everyone's own inbox: customers, shops, staff, couriers and drivers. Same API as the .NET module. */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationInboxController(
    private val inbox: NotificationInboxService,
) {
    @GetMapping
    fun list(
        authentication: JwtAuthenticationToken,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): NotificationPageResponse = inbox.page(authentication.actor().userId, status, cursor, limit)

    @GetMapping("/unread-count")
    fun unreadCount(authentication: JwtAuthenticationToken) = UnreadCountResponse(inbox.unreadCount(authentication.actor().userId))

    @PatchMapping("/{notificationId}/read")
    fun read(authentication: JwtAuthenticationToken, @PathVariable notificationId: UUID): NotificationResponse =
        inbox.setRead(authentication.actor().userId, notificationId, true)

    @PatchMapping("/{notificationId}/unread")
    fun unread(authentication: JwtAuthenticationToken, @PathVariable notificationId: UUID): NotificationResponse =
        inbox.setRead(authentication.actor().userId, notificationId, false)

    @PostMapping("/read-all")
    fun readAll(authentication: JwtAuthenticationToken) = MarkAllReadResponse(inbox.markAllRead(authentication.actor().userId))
}

/** Messages people send: managers and staff to their hub, and anyone handling a shipment to its shop. */
@RestController
@RequestMapping("/api/v1/notifications/messages")
class NotificationMessageController(
    private val messages: MessageService,
) {
    @GetMapping("/options")
    fun options(authentication: JwtAuthenticationToken): MessageOptionsResponse = messages.options(authentication.actor())

    @PostMapping("/hub")
    fun sendToHub(authentication: JwtAuthenticationToken, @Valid @RequestBody request: HubMessageRequest): SentMessageResponse =
        messages.sendToHub(authentication.actor(), request)

    @PostMapping("/shipment")
    fun sendAboutShipment(authentication: JwtAuthenticationToken, @Valid @RequestBody request: ShipmentMessageRequest): SentMessageResponse =
        messages.sendAboutShipment(authentication.actor(), request)

    @GetMapping("/sent")
    fun sent(authentication: JwtAuthenticationToken, @RequestParam(required = false) limit: Int?): List<SentMessageResponse> =
        messages.sent(authentication.actor(), limit)
}

private fun JwtAuthenticationToken.actor(): PavexActor =
    PavexActor.parse(name, authorities.mapNotNull { it.authority })
        ?: throw AccessDeniedException("Authenticated user id is invalid")
