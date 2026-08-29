package com.pavex.shipment.service

import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.dto.ShipmentPageResponse
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.ShipmentRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ShipmentQueryService(
    private val shipments: ShipmentRepository,
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun getOwned(actorUserId: UUID, rawTrackingCode: String): ShipmentDetailResponse {
        val trackingCode = ShipmentCodeNormalizer.normalizeOrNull(rawTrackingCode)
            ?.takeIf { !it.contains('-') }
            ?: throw notFound()
        val shipment = shipments.findByTrackingCodeIgnoreCaseAndCreatedByUserId(trackingCode, actorUserId)
            ?: throw notFound()
        return ShipmentResponseMapper.detail(shipment)
    }

    @Transactional(readOnly = true)
    fun listOwned(
        actorUserId: UUID,
        rawStatus: String?,
        rawCursor: String?,
        requestedLimit: Int?,
    ): ShipmentPageResponse {
        val status = rawStatus?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
            ShipmentStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw InvalidShipmentRequestException(
                    "invalid_shipment_status",
                    "Shipment status '$value' is invalid.",
                )
        }
        val cursor = rawCursor?.trim()?.takeIf(String::isNotEmpty)?.let {
            ShipmentCursorCodec.decodeOrNull(it)
                ?: throw InvalidShipmentRequestException("invalid_cursor", "Shipment cursor is invalid.")
        }
        val limit = (requestedLimit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

        val cb = entityManager.criteriaBuilder
        val query = cb.createQuery(Shipment::class.java)
        val root = query.from(Shipment::class.java)
        val predicates = mutableListOf<Predicate>(
            cb.equal(root.get<UUID>("createdByUserId"), actorUserId),
        )
        if (status != null) {
            predicates += cb.equal(root.get<ShipmentStatus>("status"), status)
        }
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

        val summaries = items.map(ShipmentResponseMapper::summary)
        val last = items.lastOrNull()
        val nextCursor = if (hasMore && last != null) {
            ShipmentCursorCodec.encode(last.createdAt, last.trackingCode)
        } else {
            null
        }
        return ShipmentPageResponse(summaries, nextCursor)
    }

    private fun notFound() = ShipmentNotFoundException(
        "shipment_not_found",
        "Shipment was not found.",
    )

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}
