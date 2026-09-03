package com.pavex.shipment.repository

import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.dto.ShipmentExceptionQueueItemResponse
import com.pavex.shipment.dto.ShipmentExceptionQueueKind
import com.pavex.shipment.dto.ShipmentExceptionQueueStatus
import com.pavex.shipment.dto.ShipmentExceptionQueueSummaryResponse
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentStatus
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ShipmentExceptionQueueRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {
    fun search(
        kind: ShipmentExceptionQueueKind?,
        status: ShipmentExceptionQueueStatus?,
        search: String?,
        scope: HubAccessScope,
        page: Int,
        pageSize: Int,
    ): ShipmentExceptionQueueSlice {
        val normalizedSearch = search?.trim()?.takeIf { it.isNotEmpty() }
        val where = buildString {
            append(" WHERE 1 = 1")
            if (!scope.isGlobal) append(hubPredicate(scope.hubIds))
            if (kind != null) append(" AND queue.kind = :kind")
            if (status != null) append(" AND queue.status = :status")
            else append(" AND queue.status IN ('PENDING_APPROVAL', 'APPROVED', 'PROCESSING', 'PENDING_REVIEW')")
            if (normalizedSearch != null) {
                append(" AND (LOWER(queue.tracking_code) LIKE :search OR LOWER(queue.reason) LIKE :search OR LOWER(COALESCE(queue.parcel_code, '')) LIKE :search)")
            }
        }

        val countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM ($BASE_QUERY) queue$where")
        bind(countQuery, kind, status, normalizedSearch, scope)
        val total = (countQuery.singleResult as Number).toLong()
        if (total == 0L) return ShipmentExceptionQueueSlice(emptyList(), 0)

        val query = entityManager.createNativeQuery(
            "SELECT * FROM ($BASE_QUERY) queue$where ORDER BY queue.requested_at DESC, queue.id DESC LIMIT :limit OFFSET :offset",
        )
        bind(query, kind, status, normalizedSearch, scope)
        query.setParameter("limit", pageSize)
        query.setParameter("offset", (page - 1) * pageSize)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return ShipmentExceptionQueueSlice(rows.map(::mapRow), total)
    }

    fun summary(scope: HubAccessScope): ShipmentExceptionQueueSummaryResponse {
        val where = if (scope.isGlobal) "" else " WHERE 1 = 1${hubPredicate(scope.hubIds)}"
        val sql = """
            SELECT
                COALESCE(SUM(CASE WHEN queue.status = 'PENDING_APPROVAL' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN queue.status IN ('APPROVED', 'PROCESSING') THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN queue.kind = 'WEIGHT_REVIEW' AND queue.status = 'PENDING_REVIEW' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN queue.kind = 'CANCELLATION' AND queue.status = 'PENDING_APPROVAL' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN queue.kind = 'RETURN' AND queue.status = 'PENDING_APPROVAL' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN queue.kind = 'FORWARD' AND queue.status = 'PENDING_APPROVAL' THEN 1 ELSE 0 END), 0)
            FROM ($BASE_QUERY) queue$where
        """.trimIndent()
        val query = entityManager.createNativeQuery(sql)
        bindScope(query, scope)
        val row = query.singleResult as Array<*>
        return ShipmentExceptionQueueSummaryResponse(
            pendingApproval = number(row[0]),
            approvedOrProcessing = number(row[1]),
            pendingWeightReview = number(row[2]),
            cancellationPending = number(row[3]),
            returnPending = number(row[4]),
            forwardPending = number(row[5]),
        )
    }

    private fun bind(
        query: jakarta.persistence.Query,
        kind: ShipmentExceptionQueueKind?,
        status: ShipmentExceptionQueueStatus?,
        search: String?,
        scope: HubAccessScope,
    ) {
        if (kind != null) query.setParameter("kind", kind.name)
        if (status != null) query.setParameter("status", status.name)
        if (search != null) query.setParameter("search", "%${search.lowercase()}%")
        bindScope(query, scope)
    }

    private fun hubPredicate(hubIds: Set<UUID>): String {
        if (hubIds.isEmpty()) return " AND 1 = 0"
        return hubIds.indices.joinToString(prefix = " AND queue.hub_id IN (", postfix = ")") { ":hub$it" }
    }

    private fun bindScope(query: jakarta.persistence.Query, scope: HubAccessScope) {
        if (scope.isGlobal) return
        scope.hubIds.toList().forEachIndexed { index, hubId -> query.setParameter("hub$index", hubId) }
    }

    private fun mapRow(row: Array<Any?>): ShipmentExceptionQueueItemResponse = ShipmentExceptionQueueItemResponse(
        id = uuid(row[0]),
        shipmentId = uuid(row[1]),
        trackingCode = row[2].toString(),
        kind = ShipmentExceptionQueueKind.valueOf(row[3].toString()),
        status = ShipmentExceptionQueueStatus.valueOf(row[4].toString()),
        reason = row[5].toString(),
        requestedAt = instant(row[6]),
        requestedByUserId = uuid(row[7]),
        reviewedByUserId = nullableUuid(row[8]),
        reviewedAt = nullableInstant(row[9]),
        parcelId = nullableUuid(row[10]),
        parcelCode = row[11]?.toString(),
        hubId = nullableUuid(row[12]),
        shipmentStatus = ShipmentStatus.valueOf(row[13].toString()),
        lifecycleStatus = ShipmentLifecycleStatus.valueOf(row[14].toString()),
        originHubCode = row[15].toString(),
        originHubName = row[16].toString(),
        destinationHubCode = row[17].toString(),
        destinationHubName = row[18].toString(),
        isOnHold = row[19] as Boolean,
        holdReason = row[20]?.toString(),
    )

    private fun uuid(value: Any?): UUID = value as? UUID ?: UUID.fromString(value.toString())
    private fun nullableUuid(value: Any?): UUID? = value?.let(::uuid)
    private fun number(value: Any?): Long = (value as Number).toLong()

    private fun instant(value: Any?): Instant = when (value) {
        is Instant -> value
        is OffsetDateTime -> value.toInstant()
        is Timestamp -> value.toInstant()
        else -> Instant.parse(value.toString())
    }

    private fun nullableInstant(value: Any?): Instant? = value?.let(::instant)

    companion object {
        private val BASE_QUERY = """
            SELECT
                request.id AS id,
                request.shipment_id AS shipment_id,
                shipment.tracking_code AS tracking_code,
                request.kind AS kind,
                request.status AS status,
                request.reason AS reason,
                request.requested_at AS requested_at,
                request.requested_by_user_id AS requested_by_user_id,
                request.reviewed_by_user_id AS reviewed_by_user_id,
                request.reviewed_at AS reviewed_at,
                NULL::uuid AS parcel_id,
                NULL::varchar AS parcel_code,
                request.hub_id AS hub_id,
                shipment.status AS shipment_status,
                shipment.lifecycle_status AS lifecycle_status,
                shipment.origin_hub_code AS origin_hub_code,
                shipment.origin_hub_name AS origin_hub_name,
                shipment.destination_hub_code AS destination_hub_code,
                shipment.destination_hub_name AS destination_hub_name,
                shipment.is_on_hold AS is_on_hold,
                shipment.hold_reason AS hold_reason
            FROM shipments.shipment_exception_requests request
            JOIN shipments.shipments shipment ON shipment.id = request.shipment_id

            UNION ALL

            SELECT
                adjustment.id AS id,
                adjustment.shipment_id AS shipment_id,
                shipment.tracking_code AS tracking_code,
                'WEIGHT_REVIEW'::varchar AS kind,
                CASE WHEN adjustment.review_completed THEN 'COMPLETED' ELSE 'PENDING_REVIEW' END::varchar AS status,
                adjustment.reason AS reason,
                adjustment.adjusted_at AS requested_at,
                adjustment.actor_user_id AS requested_by_user_id,
                adjustment.reviewed_by_user_id AS reviewed_by_user_id,
                adjustment.reviewed_at AS reviewed_at,
                adjustment.parcel_id AS parcel_id,
                parcel.code AS parcel_code,
                adjustment.hub_id AS hub_id,
                shipment.status AS shipment_status,
                shipment.lifecycle_status AS lifecycle_status,
                shipment.origin_hub_code AS origin_hub_code,
                shipment.origin_hub_name AS origin_hub_name,
                shipment.destination_hub_code AS destination_hub_code,
                shipment.destination_hub_name AS destination_hub_name,
                shipment.is_on_hold AS is_on_hold,
                shipment.hold_reason AS hold_reason
            FROM shipments.shipment_weight_adjustments adjustment
            JOIN shipments.shipments shipment ON shipment.id = adjustment.shipment_id
            JOIN shipments.parcels parcel ON parcel.id = adjustment.parcel_id
        """.trimIndent()


    }
}

data class ShipmentExceptionQueueSlice(
    val items: List<ShipmentExceptionQueueItemResponse>,
    val totalCount: Long,
)
