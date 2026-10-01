package com.pavex.shipment.service

import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.dto.ShipmentLabelsResponse
import com.pavex.shipment.dto.ShipmentPageResponse
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentChannel
import com.pavex.shipment.entity.ShipmentAddressSnapshot
import com.pavex.shipment.entity.ShipmentStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentNotFoundException
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
class ShipmentQueryService(
    private val shipments: ShipmentRepository,
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun getOwned(actorUserId: UUID, rawTrackingCode: String): ShipmentDetailResponse =
        ShipmentResponseMapper.detail(findOwned(actorUserId, rawTrackingCode))

    @Transactional(readOnly = true)
    fun labelsOwned(actorUserId: UUID, rawTrackingCode: String): ShipmentLabelsResponse =
        ShipmentResponseMapper.labels(findOwned(actorUserId, rawTrackingCode))

    private fun findOwned(actorUserId: UUID, rawTrackingCode: String): Shipment {
        val trackingCode = ShipmentCodeNormalizer.normalizeOrNull(rawTrackingCode)
            ?.takeIf { !it.contains('-') }
            ?: throw notFound()
        // Staff book counter shipments for walk-in customers; those are not their own orders.
        return shipments.findByTrackingCodeIgnoreCaseAndCreatedByUserId(trackingCode, actorUserId)
            ?.takeIf { it.channel == ShipmentChannel.MERCHANT_WEB }
            ?: throw notFound()
    }

    @Transactional(readOnly = true)
    fun listOwned(
        actorUserId: UUID,
        rawStatus: String?,
        rawCursor: String?,
        requestedLimit: Int?,
        rawSearch: String? = null,
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
            cb.equal(root.get<ShipmentChannel>("channel"), ShipmentChannel.MERCHANT_WEB),
        )
        if (status != null) {
            predicates += cb.equal(root.get<ShipmentStatus>("status"), status)
        }
        searchPredicate(cb, root, rawSearch)?.let { predicates += it }
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

    /**
     * Matches what a merchant has at hand when looking for an order: (part of) the tracking or
     * parcel code, their own order reference, the recipient's name, or (part of) their phone.
     */
    private fun searchPredicate(cb: CriteriaBuilder, root: Root<Shipment>, rawSearch: String?): Predicate? {
        val text = rawSearch?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (text.length > MAX_SEARCH_LENGTH) {
            throw InvalidShipmentRequestException("invalid_search", "Search text must not exceed $MAX_SEARCH_LENGTH characters.")
        }
        val recipient = root.get<ShipmentAddressSnapshot>("recipient")
        val contains = { value: String -> "%${escapeLike(value)}%" }
        val matches = mutableListOf(
            cb.like(root.get("trackingCode"), contains(text.uppercase().substringBefore('-')), LIKE_ESCAPE),
            cb.like(cb.lower(root.get("merchantReference")), contains(text.lowercase()), LIKE_ESCAPE),
            cb.like(cb.lower(recipient.get("contactName")), contains(text.lowercase()), LIKE_ESCAPE),
        )
        searchPhoneDigits(text)?.let { digits ->
            matches += cb.like(recipient.get("contactPhone"), contains(digits), LIKE_ESCAPE)
        }
        return cb.or(*matches.toTypedArray())
    }

    /** Stored phones are 0xxxxxxxxx, so a typed +84 prefix and separators are dropped. */
    private fun searchPhoneDigits(text: String): String? {
        if (!text.all { it.isDigit() || it in "+ .-" }) return null
        val digits = text.filter(Char::isDigit).let { if (text.trimStart().startsWith("+84")) "0${it.drop(2)}" else it }
        return digits.takeIf { it.length >= MIN_PHONE_DIGITS }
    }

    private fun escapeLike(value: String) = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private fun notFound() = ShipmentNotFoundException(
        "shipment_not_found",
        "Shipment was not found.",
    )

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_SEARCH_LENGTH = 100
        private const val MIN_PHONE_DIGITS = 4
        private const val LIKE_ESCAPE = '\\'
        const val MAX_LIMIT = 100
    }
}
