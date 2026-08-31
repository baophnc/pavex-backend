package com.pavex.shipment.service

import com.pavex.common.api.OneBasedPagination
import com.pavex.common.api.PageResponse
import com.pavex.shipment.config.ShipmentControlTowerProperties
import com.pavex.shipment.dto.ShipmentControlAlertResponse
import com.pavex.shipment.dto.ShipmentControlAlertSeverity
import com.pavex.shipment.dto.ShipmentControlAlertType
import com.pavex.shipment.dto.ShipmentControlTowerSummaryResponse
import com.pavex.shipment.entity.DeliveryAttempt
import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.DeliveryProofMethod
import com.pavex.shipment.entity.Parcel
import com.pavex.shipment.entity.ParcelStage
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentCase
import com.pavex.shipment.entity.ShipmentCaseSeverity
import com.pavex.shipment.entity.ShipmentCaseStatus
import com.pavex.shipment.entity.ShipmentControlSignal
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.repository.DeliveryAttemptRepository
import com.pavex.shipment.repository.ShipmentCaseRepository
import com.pavex.shipment.repository.ShipmentControlSignalRepository
import com.pavex.shipment.repository.ShipmentControlTowerReadRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ShipmentControlTowerService(
    private val shipmentReads: ShipmentControlTowerReadRepository,
    private val attempts: DeliveryAttemptRepository,
    private val cases: ShipmentCaseRepository,
    private val signals: ShipmentControlSignalRepository,
    private val access: ShipmentControlTowerAccessService,
    private val properties: ShipmentControlTowerProperties,
    private val metrics: ShipmentControlTowerMetrics,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun search(
        authentication: Authentication,
        type: ShipmentControlAlertType?,
        severity: ShipmentControlAlertSeverity?,
        hubId: UUID?,
        search: String?,
        page: Int,
        pageSize: Int,
    ): PageResponse<ShipmentControlAlertResponse> {
        val scope = access.resolve(authentication)
        requireHubScope(scope, hubId)
        val all = buildAlerts(scope, hubId)
        metrics.update(all)
        val needle = search?.trim()?.takeIf { it.isNotEmpty() }
        val filtered = all.asSequence()
            .filter { type == null || it.type == type }
            .filter { severity == null || it.severity == severity }
            .filter { needle == null || contains(it.trackingCode, needle) || contains(it.parcelCode, needle) || contains(it.caseNumber, needle) || contains(it.title, needle) }
            .sortedWith(compareByDescending<ShipmentControlAlertResponse> { severityRank(it.severity) }
                .thenByDescending { it.overdueMinutes ?: 0 }
                .thenBy { it.dueAt ?: it.detectedAt })
            .toList()
        val pagination = OneBasedPagination.normalize(page, pageSize, 200)
        val from = pagination.zeroBasedPage * pagination.pageSize
        val items = if (from >= filtered.size) emptyList() else filtered.drop(from).take(pagination.pageSize)
        return PageResponse(items, pagination.page, pagination.pageSize, filtered.size.toLong())
    }

    @Transactional(readOnly = true)
    fun summary(authentication: Authentication, hubId: UUID?): ShipmentControlTowerSummaryResponse {
        val scope = access.resolve(authentication)
        requireHubScope(scope, hubId)
        val alerts = buildAlerts(scope, hubId)
        metrics.update(alerts)
        return ShipmentControlTowerSummaryResponse(
            total = alerts.size,
            critical = alerts.count { it.severity == ShipmentControlAlertSeverity.CRITICAL },
            warning = alerts.count { it.severity == ShipmentControlAlertSeverity.WARNING },
            sentButNotArrived = alerts.count { it.type == ShipmentControlAlertType.SENT_BUT_NOT_ARRIVED },
            missingArrivalScan = alerts.count { it.type == ShipmentControlAlertType.MISSING_ARRIVAL_SCAN },
            arrivedNotOutForDelivery = alerts.count { it.type == ShipmentControlAlertType.ARRIVED_NOT_OUT_FOR_DELIVERY },
            routeDeviation = alerts.count { it.type == ShipmentControlAlertType.ROUTE_DEVIATION },
            shipmentStuck = alerts.count { it.type == ShipmentControlAlertType.SHIPMENT_STUCK },
            caseOverdue = alerts.count { it.type == ShipmentControlAlertType.CASE_OVERDUE },
            signatureConsistency = alerts.count { it.type == ShipmentControlAlertType.SIGNATURE_WITHOUT_DELIVERY || it.type == ShipmentControlAlertType.DELIVERED_WITHOUT_SIGNATURE },
        )
    }

    private fun buildAlerts(scope: ShipmentControlTowerScope, requestedHubId: UUID?): List<ShipmentControlAlertResponse> {
        val now = clock.instant()
        val recentThreshold = now.minus(properties.deliveryProofLookback)
        val allowedHubs = when {
            requestedHubId != null -> setOf(requestedHubId)
            scope.global -> emptySet()
            else -> scope.hubIds
        }
        val shipmentItems = shipmentReads.findCandidates(recentThreshold, allowedHubs)
        val ids = shipmentItems.map { it.id }
        val attemptItems = if (ids.isEmpty()) emptyList() else attempts.findAllByShipment_IdInAndCreatedAtGreaterThanEqual(ids, recentThreshold)
        val signalItems = if (ids.isEmpty()) emptyList() else signals.findAllByShipmentIdInAndOccurredAtGreaterThanEqual(ids, now.minus(properties.routeDeviationLookback))
        val attemptsByShipment = attemptItems.groupBy { it.shipment.id }
        val signalsByShipment = signalItems.groupBy { it.shipmentId }
        val alerts = mutableListOf<ShipmentControlAlertResponse>()

        shipmentItems.forEach { shipment ->
            shipment.parcels.size
            shipment.events.size
            addMovementAlerts(alerts, shipment, now)
            addDeliveryProofAlerts(alerts, shipment, attemptsByShipment[shipment.id].orEmpty(), now)
            addRouteDeviationAlerts(alerts, shipment, signalsByShipment[shipment.id].orEmpty())
            if (!shipment.isOnHold && alerts.none { it.shipmentId == shipment.id }) addStuckAlert(alerts, shipment, now)
        }
        addCaseAlerts(alerts, scope, requestedHubId, now)
        return alerts.asSequence()
            .filter { scope.canAccessHub(it.hubId) }
            .filter { requestedHubId == null || it.hubId == requestedHubId }
            .groupBy { it.key }
            .map { (_, values) -> values.maxBy { it.detectedAt } }
    }

    private fun addMovementAlerts(alerts: MutableList<ShipmentControlAlertResponse>, shipment: Shipment, now: Instant) {
        val destinationArrivals = shipment.parcels.filter { it.stage == ParcelStage.AT_HUB && it.currentHubId == shipment.destinationHubId }
        val firstDestinationArrival = destinationArrivals.minOfOrNull { it.updatedAt }
        shipment.parcels.forEach { parcel ->
            if (parcel.stage == ParcelStage.IN_TRANSIT) {
                val dueAt = parcel.updatedAt.plus(properties.sentNotArrivedAfter)
                if (now.isAfter(dueAt)) add(alerts, ShipmentControlAlertType.SENT_BUT_NOT_ARRIVED, severity(now, dueAt), shipment, parcel, shipment.destinationHubId, parcel.currentRouteLegId, "Đã gửi nhưng chưa đến", "Kiện đang ở trạng thái vận chuyển nhưng chưa có xác nhận đến hub tiếp theo trong thời gian SLA.", now, dueAt, parcel.updatedAt)
                if (firstDestinationArrival != null) {
                    val missingDue = firstDestinationArrival.plus(properties.missingArrivalScanGrace)
                    if (now.isAfter(missingDue)) add(alerts, ShipmentControlAlertType.MISSING_ARRIVAL_SCAN, severity(now, missingDue), shipment, parcel, shipment.destinationHubId, parcel.currentRouteLegId, "Quét sót hàng đến", "Một kiện cùng vận đơn đã đến hub đích nhưng kiện này vẫn chưa có trạng thái đến.", now, missingDue, parcel.updatedAt)
                }
            }
            if (parcel.stage == ParcelStage.AT_HUB && parcel.currentHubId == shipment.destinationHubId) {
                val dueAt = parcel.updatedAt.plus(properties.arrivedNotOutForDeliveryAfter)
                if (now.isAfter(dueAt)) add(alerts, ShipmentControlAlertType.ARRIVED_NOT_OUT_FOR_DELIVERY, severity(now, dueAt), shipment, parcel, shipment.destinationHubId, null, "Đã đến nhưng chưa phát", "Kiện đã đến hub đích nhưng chưa được bàn giao cho courier trong thời gian SLA.", now, dueAt, parcel.updatedAt)
            }
        }
    }

    private fun addDeliveryProofAlerts(alerts: MutableList<ShipmentControlAlertResponse>, shipment: Shipment, attemptItems: List<DeliveryAttempt>, now: Instant) {
        shipment.parcels.forEach { parcel ->
            val latest = attemptItems.filter { it.parcel.id == parcel.id }.maxWithOrNull(compareBy<DeliveryAttempt>({ it.attemptNumber }, { it.createdAt })) ?: return@forEach
            if (latest.proofMethod == DeliveryProofMethod.SIGNATURE && !latest.evidenceReference.isNullOrBlank() && (latest.outcome != DeliveryAttemptOutcome.DELIVERED || parcel.stage != ParcelStage.DELIVERED)) {
                add(alerts, ShipmentControlAlertType.SIGNATURE_WITHOUT_DELIVERY, ShipmentControlAlertSeverity.CRITICAL, shipment, parcel, shipment.destinationHubId, null, "Có ký nhưng chưa phát", "Đã có bằng chứng chữ ký nhưng kiện chưa ở trạng thái giao thành công.", latest.occurredAt, null, latest.occurredAt)
            }
            if (properties.requireSignatureOnDelivery && parcel.stage == ParcelStage.DELIVERED && latest.outcome == DeliveryAttemptOutcome.DELIVERED && latest.proofMethod != DeliveryProofMethod.SIGNATURE) {
                add(alerts, ShipmentControlAlertType.DELIVERED_WITHOUT_SIGNATURE, ShipmentControlAlertSeverity.WARNING, shipment, parcel, shipment.destinationHubId, null, "Đã phát chưa ký", "Kiện đã giao thành công nhưng proof không phải chữ ký theo policy hiện tại.", now, null, latest.occurredAt)
            }
        }
    }

    private fun addRouteDeviationAlerts(alerts: MutableList<ShipmentControlAlertResponse>, shipment: Shipment, signalItems: List<ShipmentControlSignal>) {
        signalItems.forEach { signal ->
            val parcel = shipment.parcels.singleOrNull { it.id == signal.parcelId }
            add(alerts, ShipmentControlAlertType.ROUTE_DEVIATION, ShipmentControlAlertSeverity.CRITICAL, shipment, parcel, signal.hubId, signal.routeLegId, "Sai tuyến / sai hub", "Scan bị từ chối bởi rule ${signal.code}: ${signal.description}", signal.occurredAt, null, signal.occurredAt, signal.id.toString())
        }
    }

    private fun addStuckAlert(alerts: MutableList<ShipmentControlAlertResponse>, shipment: Shipment, now: Instant) {
        if (shipment.lifecycleStatus != ShipmentLifecycleStatus.ACTIVE || shipment.parcels.all(::isFinal)) return
        val lastActivity = sequenceOf(shipment.updatedAt).plus(shipment.parcels.asSequence().map { it.updatedAt }).plus(shipment.events.asSequence().map { it.occurredAt }).maxOrNull() ?: shipment.updatedAt
        val dueAt = lastActivity.plus(properties.shipmentStuckAfter)
        if (!now.isAfter(dueAt)) return
        val hubId = shipment.parcels.firstOrNull { !isFinal(it) && it.currentHubId != null }?.currentHubId
            ?: if (shipment.parcels.any { it.stage == ParcelStage.IN_TRANSIT }) shipment.destinationHubId else shipment.originHubId
        add(alerts, ShipmentControlAlertType.SHIPMENT_STUCK, severity(now, dueAt), shipment, null, hubId, null, "Vận đơn bị đứng", "Vận đơn đang hoạt động nhưng không có chuyển động hoặc cập nhật trong thời gian SLA.", now, dueAt, lastActivity)
    }

    private fun addCaseAlerts(alerts: MutableList<ShipmentControlAlertResponse>, scope: ShipmentControlTowerScope, requestedHubId: UUID?, now: Instant) {
        val spec = Specification<ShipmentCase> { root, _, cb ->
            val predicates = mutableListOf(
                cb.not(root.get<ShipmentCaseStatus>("status").`in`(ShipmentCaseStatus.RESOLVED, ShipmentCaseStatus.CLOSED)),
            )
            if (!scope.global) predicates += root.get<UUID>("hubId").`in`(scope.hubIds)
            if (requestedHubId != null) predicates += cb.equal(root.get<UUID>("hubId"), requestedHubId)
            cb.and(*predicates.toTypedArray())
        }
        cases.findAll(spec).forEach { item ->
            val dueAt = item.createdAt.plus(caseSla(item.severity))
            if (!now.isAfter(dueAt)) return@forEach
            val tracking = item.shipmentId?.let { id -> shipments.findById(id).orElse(null)?.trackingCode }
            alerts += ShipmentControlAlertResponse(
                key = "CASE_OVERDUE:${item.id}",
                type = ShipmentControlAlertType.CASE_OVERDUE,
                severity = if (item.severity == ShipmentCaseSeverity.CRITICAL || Duration.between(dueAt, now) >= properties.criticalOverdueAfter) ShipmentControlAlertSeverity.CRITICAL else ShipmentControlAlertSeverity.WARNING,
                shipmentId = item.shipmentId,
                trackingCode = tracking,
                parcelId = item.parcelId,
                parcelCode = null,
                hubId = item.hubId,
                routeLegId = null,
                caseId = item.id,
                caseNumber = item.caseNumber,
                title = "Case quá SLA",
                description = "Case ${item.caseNumber} (${item.severity}) chưa được xử lý trong SLA.",
                detectedAt = now,
                dueAt = dueAt,
                overdueMinutes = overdueMinutes(now, dueAt),
                lastActivityAt = item.updatedAt,
            )
        }
    }

    private fun add(alerts: MutableList<ShipmentControlAlertResponse>, type: ShipmentControlAlertType, severity: ShipmentControlAlertSeverity, shipment: Shipment, parcel: Parcel?, hubId: UUID, routeLegId: UUID?, title: String, description: String, detectedAt: Instant, dueAt: Instant?, lastActivityAt: Instant?, suffix: String? = null) {
        alerts += ShipmentControlAlertResponse(
            key = "$type:${shipment.id}:${parcel?.id ?: "shipment"}:${routeLegId ?: suffix ?: "none"}",
            type = type,
            severity = severity,
            shipmentId = shipment.id,
            trackingCode = shipment.trackingCode,
            parcelId = parcel?.id,
            parcelCode = parcel?.code,
            hubId = hubId,
            routeLegId = routeLegId,
            caseId = null,
            caseNumber = null,
            title = title,
            description = description,
            detectedAt = detectedAt,
            dueAt = dueAt,
            overdueMinutes = dueAt?.let { overdueMinutes(detectedAt, it) },
            lastActivityAt = lastActivityAt,
        )
    }

    private fun requireHubScope(scope: ShipmentControlTowerScope, hubId: UUID?) {
        if (hubId != null && !scope.canAccessHub(hubId)) throw org.springframework.security.access.AccessDeniedException("Control tower hub is outside the current scope.")
    }

    private fun severity(now: Instant, dueAt: Instant): ShipmentControlAlertSeverity =
        if (Duration.between(dueAt, now) >= properties.criticalOverdueAfter) ShipmentControlAlertSeverity.CRITICAL else ShipmentControlAlertSeverity.WARNING

    private fun caseSla(severity: ShipmentCaseSeverity): Duration = when (severity) {
        ShipmentCaseSeverity.LOW -> properties.lowCaseSla
        ShipmentCaseSeverity.MEDIUM -> properties.mediumCaseSla
        ShipmentCaseSeverity.HIGH -> properties.highCaseSla
        ShipmentCaseSeverity.CRITICAL -> properties.criticalCaseSla
    }

    private fun isFinal(parcel: Parcel): Boolean = parcel.stage in setOf(ParcelStage.DELIVERED, ParcelStage.RETURNED, ParcelStage.CANCELLED)
    private fun overdueMinutes(now: Instant, dueAt: Instant): Long = Duration.between(dueAt, now).toMinutes().coerceAtLeast(0)
    private fun contains(value: String?, needle: String): Boolean = value?.contains(needle, ignoreCase = true) == true
    private fun severityRank(value: ShipmentControlAlertSeverity): Int = when (value) {
        ShipmentControlAlertSeverity.CRITICAL -> 3
        ShipmentControlAlertSeverity.WARNING -> 2
        ShipmentControlAlertSeverity.INFO -> 1
    }
}
