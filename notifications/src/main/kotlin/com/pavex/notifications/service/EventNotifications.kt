package com.pavex.notifications.service

import com.pavex.notifications.client.OperationsPeopleClient
import com.pavex.notifications.entity.KnownShipment
import com.pavex.notifications.entity.NotificationCategory
import com.pavex.notifications.entity.NotificationSeverity
import com.pavex.notifications.repository.KnownShipmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Turns events of other services into notices: shops hear about their shipments, workers about
 * the jobs they get or lose, and hub managers about what waits for their approval.
 */
@Service
class EventNotifications(
    private val shipments: KnownShipmentRepository,
    private val publisher: NotificationPublisher,
    private val people: OperationsPeopleClient,
) {
    // ---- Shipments, told to the shop that created them -------------------------------------

    @Transactional
    fun shipmentCreated(event: JsonNode) {
        val shipmentId = event.uuid("shipmentId") ?: return
        val shipment = shipments.findById(shipmentId).orElse(null) ?: shipments.save(
            KnownShipment(
                shipmentId = shipmentId,
                trackingCode = event.text("trackingCode") ?: return,
                createdByUserId = event.uuid("createdByUserId") ?: return,
                channel = event.text("channel") ?: "MERCHANT_WEB",
                originHubId = event.uuid("originHubId"),
                destinationHubId = event.uuid("destinationHubId"),
                createdAt = event.instant("occurredAt") ?: Instant.now(),
            ),
        )
        tellShop(shipment, "created", NotificationSeverity.Info, "Đã tạo vận đơn", "đang chờ bưu tá đến lấy hàng.")
    }

    @Transactional
    fun parcelScanned(event: JsonNode) {
        val shipment = known(event) ?: return
        val (title, body, severity) = when (event.text("currentStatus")) {
            "PICKED_UP" -> Triple("Bưu tá đã lấy hàng", "bưu tá đã nhận hàng và đang mang về bưu cục.", NotificationSeverity.Info)
            "IN_TRANSIT" -> Triple("Hàng đang trên xe trung chuyển", "hàng đã lên xe chạy tới bưu cục giao.", NotificationSeverity.Info)
            "AT_DESTINATION_HUB" -> Triple("Hàng đã tới bưu cục giao", "hàng đã tới bưu cục gần người nhận, sắp được giao.", NotificationSeverity.Info)
            "OUT_FOR_DELIVERY" -> Triple("Đang giao hàng", "bưu tá đang mang hàng đi giao cho người nhận.", NotificationSeverity.Info)
            "RETURNING" -> Triple("Hàng đang hoàn về", "hàng đang được chuyển trả về cho bạn.", NotificationSeverity.Warning)
            "RETURNED" -> Triple("Đã hoàn hàng", "hàng đã được trả về bưu cục gửi.", NotificationSeverity.Warning)
            else -> return
        }
        tellShop(shipment, event.text("currentStatus")!!.lowercase(), severity, title, body)
    }

    @Transactional
    fun deliveryAttempt(event: JsonNode) {
        val shipment = known(event) ?: return
        when (event.text("outcome")) {
            "DELIVERED" -> tellShop(shipment, "delivered", NotificationSeverity.Success, "Giao hàng thành công", "người nhận đã nhận hàng.")
            "FAILED" -> {
                val attempt = event.path("attemptNumber").asInt(1)
                tellShop(
                    shipment,
                    "failed-$attempt",
                    NotificationSeverity.Critical,
                    "Giao hàng chưa thành công",
                    "lần giao thứ $attempt chưa thành công${failureText(event.text("failureReason"))}. Bưu cục sẽ giao lại hoặc liên hệ bạn.",
                )
            }
        }
    }

    @Transactional
    fun shipmentCancelled(event: JsonNode) {
        val shipment = known(event) ?: return
        tellShop(shipment, "cancelled", NotificationSeverity.Warning, "Vận đơn đã huỷ", "vận đơn đã được huỷ.")
    }

    private fun tellShop(shipment: KnownShipment, step: String, severity: NotificationSeverity, title: String, body: String) {
        if (!shipment.notifiesCreator) return
        publisher.publish(
            PublishNotification(
                recipientUserId = shipment.createdByUserId,
                code = "shipment.$step",
                category = NotificationCategory.Shipment,
                severity = severity,
                title = title,
                body = "Vận đơn ${shipment.trackingCode}: $body",
                actionUrl = "/shipments/${shipment.trackingCode}",
                subjectType = "shipment",
                subjectId = shipment.trackingCode,
                deduplicationKey = "shipment:${shipment.shipmentId}:$step",
            ),
        )
    }

    // ---- Jobs, told to the worker -----------------------------------------------------------

    @Transactional
    fun assignment(eventType: String, event: JsonNode) {
        val userId = event.uuid("workforceUserId") ?: return
        val eventId = event.text("eventId") ?: return
        val job = jobLabel(event.text("assignmentType"))
        val trackingCode = event.uuid("shipmentId")?.let { shipments.findById(it).orElse(null)?.trackingCode }
        val about = trackingCode?.let { " vận đơn $it" } ?: ""
        val (title, body, severity) = when (eventType) {
            "operations.assignment.assigned" ->
                Triple("Việc mới: $job", "Bạn được giao $job$about. Mở app để nhận việc.", NotificationSeverity.Info)
            "operations.assignment.reassigned" ->
                Triple("Việc được chuyển cho bạn: $job", "Bạn vừa được giao lại $job$about. Mở app để nhận việc.", NotificationSeverity.Info)
            "operations.assignment.requeued" ->
                Triple("Việc đã được thu hồi", "Việc $job$about không còn giao cho bạn nữa.", NotificationSeverity.Warning)
            "operations.assignment.cancelled" ->
                Triple("Việc đã huỷ", "Việc $job$about đã huỷ, không cần làm nữa.", NotificationSeverity.Warning)
            else -> return
        }
        // Planning a shipment fires "assigned" right after "planned"; the worker gets one notice per change.
        publisher.publish(
            PublishNotification(
                recipientUserId = userId,
                code = eventType,
                category = NotificationCategory.Operations,
                severity = severity,
                title = title,
                body = body,
                actionUrl = event.uuid("assignmentId")?.let { "/operations/assignments/$it" },
                subjectType = "assignment",
                subjectId = event.text("assignmentId"),
                deduplicationKey = "assignment-event:$eventId",
            ),
        )
    }

    // ---- Waiting for the hub manager -------------------------------------------------------

    @Transactional
    fun exceptionRequested(eventType: String, event: JsonNode) {
        val data = event.path("data")
        val hubId = data.uuid("hubId") ?: return
        val kind = when {
            eventType.startsWith("shipment.return") -> "hoàn hàng"
            eventType.startsWith("shipment.forward") -> "chuyển địa chỉ"
            else -> "huỷ vận đơn"
        }
        tellManagers(
            hubId = hubId,
            except = event.uuid("actorUserId"),
            code = eventType,
            title = "Yêu cầu $kind chờ duyệt",
            body = "Vận đơn ${event.text("trackingCode")}: ${data.text("reason") ?: "không có lý do"}",
            actionUrl = "/shipments/exceptions?status=PENDING_APPROVAL",
            key = "exception:${data.text("requestId")}",
        )
    }

    @Transactional
    fun caseEvent(eventType: String, event: JsonNode) {
        val caseId = event.text("caseId") ?: return
        val caseNumber = event.text("caseNumber") ?: "hồ sơ"
        when (eventType) {
            "shipment.case.created" -> tellManagers(
                hubId = event.uuid("hubId") ?: return,
                except = event.uuid("actorUserId"),
                code = eventType,
                title = "Hồ sơ sự cố mới $caseNumber",
                body = "Có sự cố cần người kiểm tra. Mở hồ sơ để giao cho một nhân viên.",
                actionUrl = "/shipments/cases/$caseId",
                key = "case:$caseId:created",
            )
            "shipment.case.assigned" -> {
                val assignee = event.uuid("assignedToUserId") ?: return
                if (assignee == event.uuid("actorUserId")) return
                publisher.publish(
                    PublishNotification(
                        recipientUserId = assignee,
                        code = eventType,
                        category = NotificationCategory.Operations,
                        severity = NotificationSeverity.Warning,
                        title = "Bạn được giao hồ sơ sự cố $caseNumber",
                        body = "Kiểm tra kiện hàng và ghi kết luận trong hồ sơ.",
                        actionUrl = "/shipments/cases/$caseId",
                        subjectType = "case",
                        subjectId = caseId,
                        deduplicationKey = "case:$caseId:assigned:$assignee",
                    ),
                )
            }
        }
    }

    @Transactional
    fun cashSubmitted(event: JsonNode) {
        val hubId = event.uuid("hubId") ?: return
        tellManagers(
            hubId = hubId,
            except = event.uuid("courierUserId"),
            code = "billing.cash.remittance-submitted",
            title = "Tiền nộp chờ duyệt",
            body = "Bưu tá vừa nộp ${money(event.path("amount").asLong(0))} (lô ${event.text("remittanceNumber") ?: ""}). Kiểm đếm rồi duyệt.",
            actionUrl = "/finance/cash",
            key = "remittance:${event.text("remittanceId")}",
        )
    }

    private fun tellManagers(hubId: UUID, except: UUID?, code: String, title: String, body: String, actionUrl: String, key: String) {
        people.hubPeople(hubId, listOf("HUB_MANAGER"))
            .map { it.userId }
            .filter { it != except }
            .forEach { manager ->
                publisher.publish(
                    PublishNotification(
                        recipientUserId = manager,
                        code = code,
                        category = NotificationCategory.Operations,
                        severity = NotificationSeverity.Warning,
                        title = title,
                        body = body,
                        actionUrl = actionUrl,
                        deduplicationKey = key,
                    ),
                )
            }
    }

    private fun known(event: JsonNode): KnownShipment? = event.uuid("shipmentId")?.let { shipments.findById(it).orElse(null) }

    companion object {
        fun jobLabel(type: String?) = when (type) {
            "PICKUP" -> "lấy hàng"
            "ORIGIN_HUB_HANDLING" -> "xử lý tại bưu cục gửi"
            "LINE_HAUL" -> "chạy xe trung chuyển"
            "TRANSIT_HUB_HANDLING" -> "xử lý tại bưu cục trung chuyển"
            "DESTINATION_HUB_HANDLING" -> "xử lý tại bưu cục giao"
            "LAST_MILE_DELIVERY" -> "giao hàng"
            else -> "một việc"
        }

        private fun failureText(reason: String?) = when (reason) {
            "RECIPIENT_UNAVAILABLE" -> " (không gặp người nhận)"
            "RECIPIENT_REFUSED" -> " (người nhận từ chối)"
            "INVALID_ADDRESS" -> " (sai địa chỉ)"
            "UNABLE_TO_CONTACT" -> " (không liên lạc được người nhận)"
            "PAYMENT_NOT_COLLECTED" -> " (người nhận chưa trả tiền)"
            "PARCEL_DAMAGED" -> " (kiện bị hư hỏng)"
            "UNSAFE_LOCATION" -> " (nơi giao không an toàn)"
            else -> ""
        }

        private fun money(amount: Long) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫"
    }
}

internal fun JsonNode.text(field: String): String? = path(field).takeIf { it.isString || it.isNumber }?.asString()?.takeIf { it.isNotBlank() }

internal fun JsonNode.uuid(field: String): UUID? = text(field)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

internal fun JsonNode.instant(field: String): Instant? = text(field)?.let { runCatching { Instant.parse(it) }.getOrNull() }
