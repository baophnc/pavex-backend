package com.pavex.shipment.service

import com.pavex.shipment.dto.AdminDeliveryAttemptResponse
import com.pavex.shipment.dto.AdminShipmentDetailResponse
import com.pavex.shipment.dto.AdminShipmentEventResponse
import com.pavex.shipment.dto.AdminShipmentPageResponse
import com.pavex.shipment.dto.AdminShipmentSummaryResponse
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.DeliveryAttemptRepository
import com.pavex.shipment.repository.ShipmentRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AdminShipmentQueryService(
    private val shipments: ShipmentRepository,
    private val deliveryAttempts: DeliveryAttemptRepository,
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun list(
        rawStatus: String?,
        rawLifecycleStatus: String?,
        merchantId: UUID?,
        rawQuery: String?,
        rawCursor: String?,
        requestedLimit: Int?,
    ): AdminShipmentPageResponse {
        val status = parseStatus(rawStatus)
        val lifecycleStatus = parseLifecycleStatus(rawLifecycleStatus)
        val search = rawQuery?.trim()?.takeIf(String::isNotEmpty)?.also {
            if (it.length > MAX_QUERY_LENGTH) {
                throw InvalidShipmentRequestException(
                    "invalid_admin_shipment_query",
                    "Shipment search query must not exceed $MAX_QUERY_LENGTH characters.",
                )
            }
        }
        val cursor = rawCursor?.trim()?.takeIf(String::isNotEmpty)?.let {
            ShipmentCursorCodec.decodeOrNull(it)
                ?: throw InvalidShipmentRequestException("invalid_cursor", "Shipment cursor is invalid.")
        }
        val limit = (requestedLimit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val cb = entityManager.criteriaBuilder

        val totalCount = run {
            val countQuery = cb.createQuery(Long::class.java)
            val root = countQuery.from(Shipment::class.java)
            val predicates = filters(cb, root, status, lifecycleStatus, merchantId, search)
            countQuery.select(cb.count(root)).where(*predicates.toTypedArray())
            entityManager.createQuery(countQuery).singleResult
        }

        val query = cb.createQuery(Shipment::class.java)
        val root = query.from(Shipment::class.java)
        val predicates = filters(cb, root, status, lifecycleStatus, merchantId, search).toMutableList()
        if (cursor != null) {
            val createdAt = root.get<Instant>("createdAt")
            val trackingCode = root.get<String>("trackingCode")
            predicates += cb.or(
                cb.lessThan(createdAt, cursor.createdAt),
                cb.and(
                    cb.equal(createdAt, cursor.createdAt),
                    cb.lessThan(trackingCode, cursor.trackingCode),
                ),
            )
        }

        query.select(root)
            .where(*predicates.toTypedArray())
            .orderBy(
                cb.desc(root.get<Instant>("createdAt")),
                cb.desc(root.get<String>("trackingCode")),
            )

        val items = entityManager.createQuery(query)
            .setMaxResults(limit + 1)
            .resultList
            .toMutableList()
        val hasMore = items.size > limit
        if (hasMore) items.removeAt(items.lastIndex)

        val last = items.lastOrNull()
        val nextCursor = if (hasMore && last != null) {
            ShipmentCursorCodec.encode(last.createdAt, last.trackingCode)
        } else {
            null
        }

        return AdminShipmentPageResponse(
            items = items.map(::summary),
            nextCursor = nextCursor,
            totalCount = totalCount,
        )
    }

    @Transactional(readOnly = true)
    fun get(rawTrackingCode: String): AdminShipmentDetailResponse {
        val trackingCode = ShipmentCodeNormalizer.normalizeOrNull(rawTrackingCode)
            ?.takeIf { !it.contains('-') }
            ?: throw notFound()
        val shipment = shipments.findByTrackingCodeIgnoreCase(trackingCode) ?: throw notFound()
        val parcelCodes = shipment.parcels.associate { it.id to it.code }
        val attempts = deliveryAttempts.findAllByShipment_IdOrderByOccurredAtAscCreatedAtAsc(shipment.id)

        return AdminShipmentDetailResponse(
            id = shipment.id,
            merchantId = shipment.merchantId,
            createdByUserId = shipment.createdByUserId,
            quoteId = shipment.quoteId,
            quoteRequestId = shipment.quoteRequestId,
            ratePlanId = shipment.ratePlanId,
            rateRevision = shipment.rateRevision,
            capacityReservationBatchId = shipment.capacityReservationBatchId,
            originServiceAreaId = shipment.originServiceAreaId,
            originRegionId = shipment.originRegionId,
            originHubId = shipment.originHubId,
            destinationServiceAreaId = shipment.destinationServiceAreaId,
            destinationRegionId = shipment.destinationRegionId,
            destinationHubId = shipment.destinationHubId,
            version = shipment.version,
            shipment = ShipmentResponseMapper.detail(shipment),
            events = shipment.events
                .sortedWith(compareBy({ it.occurredAt }, { it.recordedAt }))
                .map { event ->
                    AdminShipmentEventResponse(
                        id = event.id,
                        status = event.status,
                        code = event.code,
                        description = event.description,
                        parcelCode = event.parcelId?.let(parcelCodes::get),
                        actorUserId = event.actorUserId,
                        hubId = event.hubId,
                        locationName = event.locationName,
                        dataJson = event.dataJson,
                        occurredAt = event.occurredAt,
                        recordedAt = event.recordedAt,
                    )
                },
            deliveryAttempts = attempts.map { attempt ->
                AdminDeliveryAttemptResponse(
                    id = attempt.id,
                    parcelCode = attempt.parcel.code,
                    actorUserId = attempt.actorUserId,
                    attemptNumber = attempt.attemptNumber,
                    outcome = attempt.outcome,
                    failureReason = attempt.failureReason,
                    proofMethod = attempt.proofMethod,
                    recipientName = attempt.recipientName,
                    evidenceReference = attempt.evidenceReference,
                    note = attempt.note,
                    latitude = attempt.latitude,
                    longitude = attempt.longitude,
                    occurredAt = attempt.occurredAt,
                    createdAt = attempt.createdAt,
                )
            },
        )
    }

    private fun filters(
        cb: CriteriaBuilder,
        root: Root<Shipment>,
        status: ShipmentStatus?,
        lifecycleStatus: ShipmentLifecycleStatus?,
        merchantId: UUID?,
        search: String?,
    ): List<Predicate> = buildList {
        if (status != null) add(cb.equal(root.get<ShipmentStatus>("status"), status))
        if (lifecycleStatus != null) {
            add(cb.equal(root.get<ShipmentLifecycleStatus>("lifecycleStatus"), lifecycleStatus))
        }
        if (merchantId != null) add(cb.equal(root.get<UUID>("merchantId"), merchantId))
        if (search != null) {
            val pattern = "%${search.lowercase()}%"
            add(
                cb.or(
                    cb.like(cb.lower(root.get<String>("trackingCode")), pattern),
                    cb.like(cb.lower(root.get<String>("merchantReference")), pattern),
                ),
            )
        }
    }

    private fun parseStatus(rawStatus: String?): ShipmentStatus? =
        rawStatus?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
            ShipmentStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw InvalidShipmentRequestException(
                    "invalid_shipment_status",
                    "Shipment status '$value' is invalid.",
                )
        }

    private fun parseLifecycleStatus(rawLifecycleStatus: String?): ShipmentLifecycleStatus? =
        rawLifecycleStatus?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
            ShipmentLifecycleStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw InvalidShipmentRequestException(
                    "invalid_shipment_lifecycle_status",
                    "Shipment lifecycle status '$value' is invalid.",
                )
        }

    private fun summary(shipment: Shipment) = AdminShipmentSummaryResponse(
        id = shipment.id,
        trackingCode = shipment.trackingCode,
        merchantId = shipment.merchantId,
        createdByUserId = shipment.createdByUserId,
        merchantReference = shipment.merchantReference,
        status = shipment.status,
        lifecycleStatus = shipment.lifecycleStatus,
        serviceLevel = shipment.serviceLevel,
        shippingFee = shipment.shippingFee,
        codAmount = shipment.codAmount,
        destinationProvinceCode = shipment.recipient.provinceCode,
        destinationProvinceName = shipment.recipient.provinceName,
        destinationWardCode = shipment.recipient.wardCode,
        destinationWardName = shipment.recipient.wardName,
        originHubCode = shipment.originHubCode,
        originHubName = shipment.originHubName,
        destinationHubCode = shipment.destinationHubCode,
        destinationHubName = shipment.destinationHubName,
        parcelCount = shipment.parcels.size,
        createdAt = shipment.createdAt,
        updatedAt = shipment.updatedAt,
    )

    private fun notFound() = ShipmentNotFoundException(
        "shipment_not_found",
        "Shipment was not found.",
    )

    companion object {
        const val DEFAULT_LIMIT = 25
        const val MAX_LIMIT = 100
        const val MAX_QUERY_LENGTH = 120
    }
}
