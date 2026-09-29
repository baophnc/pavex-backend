package com.pavex.shipment.service

import com.pavex.common.security.PavexActor
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.WorkforceReportItem
import com.pavex.shipment.dto.WorkforceReportKind
import com.pavex.shipment.dto.WorkforceReportSummaryResponse
import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentEvent
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentForbiddenException
import com.pavex.shipment.repository.DeliveryAttemptRepository
import com.pavex.shipment.repository.ShipmentRepository
import com.pavex.shipment.repository.WorkforceReportRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Daily figures for the field app: a courier's own work, or (for hub staff) everything that
 * moved through one of their hubs. Days follow Vietnam time.
 */
@Service
class WorkforceReportService(
    private val reports: WorkforceReportRepository,
    private val shipments: ShipmentRepository,
    private val attempts: DeliveryAttemptRepository,
    private val operations: OperationsShipmentClient,
) {
    @Transactional(readOnly = true)
    fun summary(actor: PavexActor, from: LocalDate, to: LocalDate, hubId: UUID?): WorkforceReportSummaryResponse {
        val range = range(from, to)
        hubId?.let { requireHub(actor, it) }
        val counts = if (hubId == null) {
            reports.countByActor(actor.userId, range.first, range.second, EVENT_CODES)
        } else {
            reports.countByHub(hubId, range.first, range.second, EVENT_CODES)
        }.associate { it.getCode() to it.getTotal() }
        val collected = if (hubId == null) {
            reports.collectedByActor(actor.userId, range.first, range.second)
        } else {
            reports.collectedByHub(hubId, range.first, range.second)
        }
        return WorkforceReportSummaryResponse(
            from = from,
            to = to,
            hubId = hubId,
            pickedUp = counts["parcel.picked-up"] ?: 0,
            received = RECEIVED_CODES.sumOf { counts[it] ?: 0 },
            dispatched = counts["parcel.in-transit"] ?: 0,
            outForDelivery = counts["parcel.out-for-delivery"] ?: 0,
            delivered = counts["parcel.delivered"] ?: 0,
            deliveryFailed = counts["parcel.delivery-failed"] ?: 0,
            cancelled = counts["shipment.cancelled"] ?: 0,
            returning = counts["parcel.returning"] ?: 0,
            returned = counts["parcel.returned"] ?: 0,
            inStock = hubId?.let { reports.countInStock(it, IN_STOCK) } ?: 0,
            collectedAmount = collected.getAmount() ?: 0,
            collectedShipments = collected.getShipments(),
        )
    }

    @Transactional(readOnly = true)
    fun items(
        actor: PavexActor,
        kind: WorkforceReportKind,
        from: LocalDate,
        to: LocalDate,
        hubId: UUID?,
    ): List<WorkforceReportItem> {
        hubId?.let { requireHub(actor, it) }
        if (kind == WorkforceReportKind.IN_STOCK) {
            val hub = hubId ?: throw InvalidShipmentRequestException("hub_required", "Chọn bưu cục để xem hàng tồn kho.")
            val ids = reports.shipmentIdsInStock(hub, IN_STOCK, PageRequest.of(0, LIMIT)).distinct()
            val byId = shipments.findAllById(ids).associateBy { it.id }
            return ids.mapNotNull(byId::get).map { item(it, null, it.updatedAt) }
        }

        val range = range(from, to)
        val code = KIND_CODES.getValue(kind)
        val page = PageRequest.of(0, LIMIT)
        val events = if (hubId == null) {
            reports.eventsByActor(actor.userId, range.first, range.second, code, page)
        } else {
            reports.eventsByHub(hubId, range.first, range.second, code, page)
        }
        return events.distinctBy { it.shipment.id }.map { event -> item(event.shipment, reason(kind, event), event.occurredAt) }
    }

    private fun reason(kind: WorkforceReportKind, event: ShipmentEvent): String? = when (kind) {
        WorkforceReportKind.CANCELLED -> event.shipment.cancellationReason
        WorkforceReportKind.DELIVERY_FAILED -> attempts
            .findAllByShipment_IdOrderByOccurredAtAscCreatedAtAsc(event.shipment.id)
            .lastOrNull { it.outcome == DeliveryAttemptOutcome.FAILED }
            ?.let { ShipmentVietnameseText.deliveryFailure(it.failureReason) }
        else -> ShipmentVietnameseText.event(event.description)
    }

    private fun item(shipment: Shipment, reason: String?, occurredAt: Instant) = WorkforceReportItem(
        trackingCode = shipment.trackingCode,
        status = shipment.status,
        recipientName = shipment.recipient.contactName,
        recipientPhone = shipment.recipient.contactPhone,
        recipientAddress = listOf(
            shipment.recipient.addressLine,
            shipment.recipient.wardName,
            shipment.recipient.provinceName,
        ).filter { it.isNotBlank() }.joinToString(", "),
        codAmount = shipment.codAmount,
        amountToCollect = shipment.recipientCollectAmount,
        reason = reason,
        occurredAt = occurredAt,
    )

    private fun requireHub(actor: PavexActor, hubId: UUID) {
        if (actor.seesAllHubs && actor.hasAuthority(WorkforceViewService.ADMIN_READ)) return
        if (!actor.hasAuthority(WorkforceViewService.HUB_READ) || hubId !in operations.caseAdminScope(actor.userId).hubIds) {
            throw ShipmentForbiddenException("hub_report_forbidden", "Bạn chỉ xem được báo cáo của bưu cục mình.")
        }
    }

    private fun range(from: LocalDate, to: LocalDate): Pair<Instant, Instant> {
        if (to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw InvalidShipmentRequestException("invalid_report_range", "Chọn khoảng thời gian tối đa $MAX_DAYS ngày.")
        }
        return from.atStartOfDay(ZONE).toInstant() to to.plusDays(1).atStartOfDay(ZONE).toInstant()
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        private const val MAX_DAYS = 92L
        private const val LIMIT = 300

        private val RECEIVED_CODES = listOf("parcel.at-origin-hub", "parcel.at-transit-hub", "parcel.at-destination-hub")
        private val EVENT_CODES = RECEIVED_CODES + listOf(
            "parcel.picked-up",
            "parcel.in-transit",
            "parcel.out-for-delivery",
            "parcel.delivered",
            "parcel.delivery-failed",
            "parcel.returning",
            "parcel.returned",
            "shipment.cancelled",
        )
        private val KIND_CODES = mapOf(
            WorkforceReportKind.DELIVERED to "parcel.delivered",
            WorkforceReportKind.DELIVERY_FAILED to "parcel.delivery-failed",
            WorkforceReportKind.CANCELLED to "shipment.cancelled",
            WorkforceReportKind.RETURNING to "parcel.returning",
            WorkforceReportKind.RETURNED to "parcel.returned",
        )
        private val IN_STOCK = listOf(ParcelStatus.AT_ORIGIN_HUB, ParcelStatus.AT_TRANSIT_HUB, ParcelStatus.AT_DESTINATION_HUB)
    }
}
