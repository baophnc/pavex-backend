package com.pavex.shipment.service

import tools.jackson.databind.ObjectMapper
import com.pavex.common.api.OneBasedPagination
import com.pavex.common.api.PageResponse
import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.dto.ShipmentCaseEventResponse
import com.pavex.shipment.dto.ShipmentCaseMonitoringSummary
import com.pavex.shipment.dto.ShipmentCaseResponse
import com.pavex.shipment.dto.ShipmentCaseSummaryResponse
import com.pavex.shipment.entity.ShipmentCase
import com.pavex.shipment.entity.ShipmentCaseSeverity
import com.pavex.shipment.entity.ShipmentCaseStatus
import com.pavex.shipment.entity.ShipmentCaseType
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentForbiddenException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.exception.UnprocessableShipmentException
import com.pavex.shipment.repository.ShipmentCaseRepository
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ShipmentCaseService(
    private val cases: ShipmentCaseRepository,
    private val shipments: ShipmentRepository,
    private val network: NetworkShipmentClient,
    private val access: ShipmentCaseAccessService,
    private val outbox: ShipmentOutboxService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun createProblem(
        authentication: Authentication,
        shipmentId: UUID,
        parcelId: UUID?,
        hubId: UUID,
        severity: ShipmentCaseSeverity,
        title: String,
        description: String,
    ): ShipmentCaseResponse {
        val scope = access.resolve(authentication)
        requireHub(scope, hubId)
        val shipment = shipments.findById(shipmentId).orElseThrow {
            UnprocessableShipmentException("case_shipment_not_found", "Shipment was not found.")
        }
        if (parcelId != null && shipment.parcels.none { it.id == parcelId }) {
            throw UnprocessableShipmentException("case_parcel_not_found", "Parcel does not belong to the shipment.")
        }
        val now = clock.instant()
        val item = domain {
            ShipmentCase.createProblem(shipmentId, parcelId, hubId, severity, title, description, actor(authentication), now)
        }
        cases.saveAndFlush(item)
        publishLatest(item)
        return response(item, shipment)
    }

    @Transactional
    fun createUnidentified(
        authentication: Authentication,
        hubId: UUID,
        severity: ShipmentCaseSeverity,
        title: String,
        description: String,
        externalBarcode: String?,
    ): ShipmentCaseResponse {
        val scope = access.resolve(authentication)
        requireHub(scope, hubId)
        val now = clock.instant()
        val item = domain {
            ShipmentCase.createUnidentified(hubId, severity, title, description, externalBarcode, actor(authentication), now)
        }
        cases.saveAndFlush(item)
        publishLatest(item)
        return response(item, null)
    }

    @Transactional(readOnly = true)
    fun search(
        authentication: Authentication,
        type: ShipmentCaseType?,
        status: ShipmentCaseStatus?,
        severity: ShipmentCaseSeverity?,
        hubId: UUID?,
        assignedToUserId: UUID?,
        shipmentId: UUID?,
        search: String?,
        page: Int,
        pageSize: Int,
    ): PageResponse<ShipmentCaseSummaryResponse> {
        val scope = access.resolve(authentication)
        if (hubId != null && !scope.canAccessHub(hubId)) forbidden()
        val pagination = OneBasedPagination.normalize(page, pageSize)
        val spec = specification(scope, type, status, severity, hubId, assignedToUserId, shipmentId, search)
        val result = cases.findAll(
            spec,
            PageRequest.of(
                pagination.zeroBasedPage,
                pagination.pageSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")),
            ),
        )
        val linkedShipments = shipments.findAllById(result.content.mapNotNull { it.shipmentId }.distinct()).associateBy { it.id }
        return PageResponse(
            items = result.content.map { summary(it, it.shipmentId?.let(linkedShipments::get)) },
            page = pagination.page,
            pageSize = pagination.pageSize,
            totalCount = result.totalElements,
        )
    }

    @Transactional(readOnly = true)
    fun monitoringSummary(authentication: Authentication, hubId: UUID?): ShipmentCaseMonitoringSummary {
        val scope = access.resolve(authentication)
        if (hubId != null && !scope.canAccessHub(hubId)) forbidden()
        val now = clock.instant()
        val today = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        val tomorrow = today.plus(1, ChronoUnit.DAYS)
        fun count(extra: Specification<ShipmentCase>? = null): Long {
            val base = specification(scope, hubId = hubId)
            return cases.count(if (extra == null) base else base.and(extra))
        }
        return ShipmentCaseMonitoringSummary(
            totalActive = count(fieldNotIn("status", setOf(ShipmentCaseStatus.RESOLVED, ShipmentCaseStatus.CLOSED))),
            open = count(fieldEquals("status", ShipmentCaseStatus.OPEN)),
            assigned = count(fieldEquals("status", ShipmentCaseStatus.ASSIGNED)),
            investigating = count(fieldEquals("status", ShipmentCaseStatus.INVESTIGATING)),
            problemParcels = count(
                fieldEquals<ShipmentCaseType>("type", ShipmentCaseType.PROBLEM_PARCEL)
                    .and(fieldNotIn("status", setOf(ShipmentCaseStatus.RESOLVED, ShipmentCaseStatus.CLOSED))),
            ),
            unidentifiedParcels = count(
                fieldEquals<ShipmentCaseType>("type", ShipmentCaseType.UNIDENTIFIED_PARCEL)
                    .and(fieldNotIn("status", setOf(ShipmentCaseStatus.RESOLVED, ShipmentCaseStatus.CLOSED))),
            ),
            highOrCritical = count(
                fieldIn("severity", setOf(ShipmentCaseSeverity.HIGH, ShipmentCaseSeverity.CRITICAL))
                    .and(fieldNotIn("status", setOf(ShipmentCaseStatus.RESOLVED, ShipmentCaseStatus.CLOSED))),
            ),
            resolvedToday = count(Specification { root, _, cb ->
                cb.and(cb.greaterThanOrEqualTo(root.get("resolvedAt"), today), cb.lessThan(root.get("resolvedAt"), tomorrow))
            }),
        )
    }

    @Transactional(readOnly = true)
    fun get(authentication: Authentication, caseId: UUID): ShipmentCaseResponse {
        val scope = access.resolve(authentication)
        val item = cases.findDetailedById(caseId) ?: throw notFound()
        if (!scope.canAccessHub(item.hubId)) forbidden()
        return responseWithShipment(item)
    }

    @Transactional
    fun assign(authentication: Authentication, caseId: UUID, assignedToUserId: UUID, note: String?): ShipmentCaseResponse =
        mutate(authentication, caseId) { item, now -> item.assign(assignedToUserId, actor(authentication), note, now) }

    @Transactional
    fun investigate(authentication: Authentication, caseId: UUID, note: String?): ShipmentCaseResponse =
        mutate(authentication, caseId) { item, now -> item.startInvestigation(actor(authentication), note, now) }

    @Transactional
    fun addNote(authentication: Authentication, caseId: UUID, note: String): ShipmentCaseResponse =
        mutate(authentication, caseId) { item, now -> item.addNote(actor(authentication), note, now) }

    @Transactional
    fun link(
        authentication: Authentication,
        caseId: UUID,
        shipmentId: UUID,
        parcelId: UUID?,
        note: String?,
    ): ShipmentCaseResponse {
        val scope = access.resolve(authentication)
        val item = cases.findForUpdateById(caseId) ?: throw notFound()
        if (!scope.canAccessHub(item.hubId)) forbidden()
        val shipment = shipments.findById(shipmentId).orElseThrow {
            UnprocessableShipmentException("case_shipment_not_found", "Shipment was not found.")
        }
        if (parcelId != null && shipment.parcels.none { it.id == parcelId }) {
            throw UnprocessableShipmentException("case_parcel_not_found", "Parcel does not belong to the shipment.")
        }
        mutateLoaded(item) { now -> item.linkToShipment(shipmentId, parcelId, actor(authentication), note, now) }
        return response(item, shipment)
    }

    @Transactional
    fun resolve(authentication: Authentication, caseId: UUID, resolution: String): ShipmentCaseResponse =
        mutate(authentication, caseId) { item, now -> item.resolve(actor(authentication), resolution, now) }

    @Transactional
    fun close(authentication: Authentication, caseId: UUID, note: String?): ShipmentCaseResponse =
        mutate(authentication, caseId) { item, now -> item.close(actor(authentication), note, now) }

    private fun mutate(
        authentication: Authentication,
        caseId: UUID,
        action: (ShipmentCase, Instant) -> Unit,
    ): ShipmentCaseResponse {
        val scope = access.resolve(authentication)
        val item = cases.findForUpdateById(caseId) ?: throw notFound()
        if (!scope.canAccessHub(item.hubId)) forbidden()
        mutateLoaded(item) { now -> action(item, now) }
        return responseWithShipment(item)
    }

    private fun mutateLoaded(item: ShipmentCase, action: (Instant) -> Unit) {
        try {
            action(clock.instant())
            cases.saveAndFlush(item)
            publishLatest(item)
        } catch (exception: OptimisticLockingFailureException) {
            throw ShipmentConflictException("case_concurrency_conflict", "Case changed while the request was being processed.")
        } catch (exception: IllegalStateException) {
            throw ShipmentConflictException("case_invalid_state", exception.message ?: "Case state transition is invalid.")
        } catch (exception: IllegalArgumentException) {
            throw UnprocessableShipmentException("case_invalid", exception.message ?: "Case request is invalid.")
        }
    }

    private fun requireHub(scope: ShipmentCaseAccessScope, hubId: UUID) {
        if (!scope.canAccessHub(hubId)) forbidden()
        val hub = network.resolveHub(hubId)
        if (!hub.active) throw UnprocessableShipmentException("case_hub_inactive", "The case hub is inactive.")
    }

    private fun publishLatest(item: ShipmentCase) {
        val event = item.events.maxWithOrNull(compareBy({ it.occurredAt }, { it.id })) ?: return
        outbox.enqueue(
            aggregateId = item.id,
            aggregateCode = item.caseNumber,
            eventType = "shipment.${event.code}",
            payloadJson = objectMapper.writeValueAsString(
                mapOf(
                    "caseId" to item.id,
                    "caseNumber" to item.caseNumber,
                    "caseType" to item.type,
                    "status" to item.status,
                    "severity" to item.severity,
                    "hubId" to item.hubId,
                    "shipmentId" to item.shipmentId,
                    "parcelId" to item.parcelId,
                    "assignedToUserId" to item.assignedToUserId,
                    "eventCode" to event.code,
                    "actorUserId" to event.actorUserId,
                    "occurredAt" to event.occurredAt,
                ),
            ),
            occurredAt = event.occurredAt,
        )
    }

    private fun specification(
        scope: ShipmentCaseAccessScope,
        type: ShipmentCaseType? = null,
        status: ShipmentCaseStatus? = null,
        severity: ShipmentCaseSeverity? = null,
        hubId: UUID? = null,
        assignedToUserId: UUID? = null,
        shipmentId: UUID? = null,
        search: String? = null,
    ): Specification<ShipmentCase> = Specification { root, _, cb ->
        val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
        if (!scope.isGlobal) predicates += root.get<UUID>("hubId").`in`(scope.hubIds)
        type?.let { predicates += cb.equal(root.get<ShipmentCaseType>("type"), it) }
        status?.let { predicates += cb.equal(root.get<ShipmentCaseStatus>("status"), it) }
        severity?.let { predicates += cb.equal(root.get<ShipmentCaseSeverity>("severity"), it) }
        hubId?.let { predicates += cb.equal(root.get<UUID>("hubId"), it) }
        assignedToUserId?.let { predicates += cb.equal(root.get<UUID>("assignedToUserId"), it) }
        shipmentId?.let { predicates += cb.equal(root.get<UUID>("shipmentId"), it) }
        search?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()?.let { text ->
            val pattern = "%${escapeLike(text)}%"
            predicates += cb.or(
                cb.like(cb.lower(root.get("caseNumber")), pattern, '\\'),
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("externalBarcode")), pattern, '\\'),
            )
        }
        cb.and(*predicates.toTypedArray())
    }

    private fun <T> fieldEquals(field: String, value: T): Specification<ShipmentCase> =
        Specification { root, _, cb -> cb.equal(root.get<T>(field), value) }

    private fun <T> fieldIn(field: String, values: Set<T>): Specification<ShipmentCase> =
        Specification { root, _, _ -> root.get<T>(field).`in`(values) }

    private fun <T> fieldNotIn(field: String, values: Set<T>): Specification<ShipmentCase> =
        Specification { root, _, cb -> cb.not(root.get<T>(field).`in`(values)) }

    private fun responseWithShipment(item: ShipmentCase): ShipmentCaseResponse {
        val shipment = item.shipmentId?.let { shipments.findById(it).orElse(null) }
        return response(item, shipment)
    }

    private fun response(item: ShipmentCase, shipment: Shipment?) = ShipmentCaseResponse(
        id = item.id,
        caseNumber = item.caseNumber,
        type = item.type,
        status = item.status,
        severity = item.severity,
        hubId = item.hubId,
        shipmentId = item.shipmentId,
        trackingCode = shipment?.trackingCode,
        parcelId = item.parcelId,
        parcelCode = item.parcelId?.let { parcelId -> shipment?.parcels?.firstOrNull { it.id == parcelId }?.code },
        externalBarcode = item.externalBarcode,
        title = item.title,
        description = item.description,
        reportedByUserId = item.reportedByUserId,
        assignedToUserId = item.assignedToUserId,
        resolution = item.resolution,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
        resolvedAt = item.resolvedAt,
        closedAt = item.closedAt,
        version = item.version,
        events = item.events.sortedWith(compareBy({ it.occurredAt }, { it.id })).map {
            ShipmentCaseEventResponse(it.id, it.code, it.description, it.actorUserId, it.occurredAt, it.dataJson)
        },
    )

    private fun summary(item: ShipmentCase, shipment: Shipment?) = ShipmentCaseSummaryResponse(
        id = item.id,
        caseNumber = item.caseNumber,
        type = item.type,
        status = item.status,
        severity = item.severity,
        hubId = item.hubId,
        shipmentId = item.shipmentId,
        trackingCode = shipment?.trackingCode,
        parcelId = item.parcelId,
        parcelCode = item.parcelId?.let { parcelId -> shipment?.parcels?.firstOrNull { it.id == parcelId }?.code },
        externalBarcode = item.externalBarcode,
        title = item.title,
        assignedToUserId = item.assignedToUserId,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
    )

    private fun actor(authentication: Authentication): UUID = runCatching { UUID.fromString(authentication.name) }
        .getOrElse { throw ShipmentForbiddenException("case_forbidden", "Authenticated user id is invalid.") }

    private fun forbidden(): Nothing = throw ShipmentForbiddenException(
        "case_forbidden",
        "You cannot access this shipment case scope.",
    )

    private fun notFound() = ShipmentNotFoundException("case_not_found", "Shipment case was not found.")

    private fun <T> domain(block: () -> T): T = try {
        block()
    } catch (exception: IllegalArgumentException) {
        throw UnprocessableShipmentException("case_invalid", exception.message ?: "Case request is invalid.")
    }

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
