package com.pavex.shipment.service

import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.exception.InvalidShipmentRequestException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ShipmentDayStats(
    val date: LocalDate,
    /** Shipments booked that day, and the shipping fees they were priced at. */
    val created: Long,
    val bookedFees: Long,
    val delivered: Long,
    /** Shipments with at least one failed delivery attempt that day. */
    val failed: Long,
    val cancelled: Long,
    val returned: Long,
)

data class HubStats(
    val hubId: UUID,
    val created: Long,
    val bookedFees: Long,
    val delivered: Long,
    val failed: Long,
)

data class ShipmentStatsResponse(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<ShipmentDayStats>,
    /** Per hub, for head office looking at all hubs; empty otherwise. */
    val hubs: List<HubStats>,
)

/**
 * Daily figures for the overview: bookings and their fees count at the origin hub, deliveries and
 * failed attempts at the destination hub. Days are Vietnamese calendar days.
 */
@Service
class ShipmentStatsService(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun stats(scope: HubAccessScope, from: LocalDate, to: LocalDate, hubId: UUID?): ShipmentStatsResponse {
        if (from.isAfter(to)) throw InvalidShipmentRequestException("invalid_date_range", "from must not be after to.")
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw InvalidShipmentRequestException("date_range_too_long", "Choose at most $MAX_DAYS days.")
        }
        if (hubId != null && !scope.canAccessHub(hubId)) {
            throw InvalidShipmentRequestException("hub_out_of_scope", "You can only see the hubs you work at.")
        }
        val hubs: Set<UUID>? = when {
            hubId != null -> setOf(hubId)
            scope.isGlobal -> null
            else -> scope.hubIds
        }
        val start = from.atStartOfDay(VIETNAM).toInstant()
        val end = to.plusDays(1).atStartOfDay(VIETNAM).toInstant()
        val params = MapSqlParameterSource()
            .addValue("start", java.sql.Timestamp.from(start))
            .addValue("end", java.sql.Timestamp.from(end))
            .addValue("from", java.sql.Date.valueOf(from))
            .addValue("to", java.sql.Date.valueOf(to))
            .addValue("allHubs", hubs == null)
            // IN () is not SQL; a hub that cannot exist keeps the list non-empty when all hubs count.
            .addValue("hubs", hubs?.toList()?.ifEmpty { null } ?: listOf(UUID(0, 0)))

        val days = jdbc.query(DAILY_SQL, params) { rs, _ ->
            ShipmentDayStats(
                date = rs.getDate("day").toLocalDate(),
                created = rs.getLong("created"),
                bookedFees = rs.getLong("booked_fees"),
                delivered = rs.getLong("delivered"),
                failed = rs.getLong("failed"),
                cancelled = rs.getLong("cancelled"),
                returned = rs.getLong("returned"),
            )
        }
        val perHub = if (hubs == null) {
            jdbc.query(HUB_SQL, params) { rs, _ ->
                HubStats(
                    hubId = rs.getObject("hub_id", UUID::class.java),
                    created = rs.getLong("created"),
                    bookedFees = rs.getLong("booked_fees"),
                    delivered = rs.getLong("delivered"),
                    failed = rs.getLong("failed"),
                )
            }
        } else {
            emptyList()
        }
        return ShipmentStatsResponse(from, to, days, perHub)
    }

    companion object {
        private val VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        const val MAX_DAYS = 92L

        private const val IN_SCOPE_ORIGIN = "(:allHubs OR s.origin_hub_id IN (:hubs))"
        private const val IN_SCOPE_DESTINATION = "(:allHubs OR s.destination_hub_id IN (:hubs))"
        private const val LOCAL_DAY = "AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"

        private val DAILY_SQL = """
            WITH days AS (
                SELECT generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day')::date AS day
            ),
            created AS (
                SELECT (s.created_at $LOCAL_DAY AS day, count(*) AS n, coalesce(sum(s.shipping_fee), 0) AS fees
                FROM shipments.shipments s
                WHERE s.created_at >= :start AND s.created_at < :end AND $IN_SCOPE_ORIGIN
                GROUP BY 1
            ),
            delivered AS (
                SELECT (s.completed_at $LOCAL_DAY AS day, count(*) AS n
                FROM shipments.shipments s
                WHERE s.status = 'DELIVERED' AND s.completed_at >= :start AND s.completed_at < :end AND $IN_SCOPE_DESTINATION
                GROUP BY 1
            ),
            failed AS (
                SELECT (a.occurred_at $LOCAL_DAY AS day, count(DISTINCT a.shipment_id) AS n
                FROM shipments.delivery_attempts a JOIN shipments.shipments s ON s.id = a.shipment_id
                WHERE a.outcome = 'FAILED' AND a.occurred_at >= :start AND a.occurred_at < :end AND $IN_SCOPE_DESTINATION
                GROUP BY 1
            ),
            cancelled AS (
                SELECT (s.cancelled_at $LOCAL_DAY AS day, count(*) AS n
                FROM shipments.shipments s
                WHERE s.cancelled_at >= :start AND s.cancelled_at < :end AND $IN_SCOPE_ORIGIN
                GROUP BY 1
            ),
            returned AS (
                SELECT (s.completed_at $LOCAL_DAY AS day, count(*) AS n
                FROM shipments.shipments s
                WHERE s.status = 'RETURNED' AND s.completed_at >= :start AND s.completed_at < :end AND $IN_SCOPE_ORIGIN
                GROUP BY 1
            )
            SELECT d.day,
                   coalesce(c.n, 0) AS created, coalesce(c.fees, 0) AS booked_fees,
                   coalesce(dl.n, 0) AS delivered, coalesce(f.n, 0) AS failed,
                   coalesce(x.n, 0) AS cancelled, coalesce(r.n, 0) AS returned
            FROM days d
            LEFT JOIN created c ON c.day = d.day
            LEFT JOIN delivered dl ON dl.day = d.day
            LEFT JOIN failed f ON f.day = d.day
            LEFT JOIN cancelled x ON x.day = d.day
            LEFT JOIN returned r ON r.day = d.day
            ORDER BY d.day
        """.trimIndent()

        private val HUB_SQL = """
            WITH created AS (
                SELECT s.origin_hub_id AS hub_id, count(*) AS n, coalesce(sum(s.shipping_fee), 0) AS fees
                FROM shipments.shipments s WHERE s.created_at >= :start AND s.created_at < :end
                GROUP BY 1
            ),
            delivered AS (
                SELECT s.destination_hub_id AS hub_id, count(*) AS n
                FROM shipments.shipments s
                WHERE s.status = 'DELIVERED' AND s.completed_at >= :start AND s.completed_at < :end
                GROUP BY 1
            ),
            failed AS (
                SELECT s.destination_hub_id AS hub_id, count(DISTINCT a.shipment_id) AS n
                FROM shipments.delivery_attempts a JOIN shipments.shipments s ON s.id = a.shipment_id
                WHERE a.outcome = 'FAILED' AND a.occurred_at >= :start AND a.occurred_at < :end
                GROUP BY 1
            ),
            hubs AS (SELECT hub_id FROM created UNION SELECT hub_id FROM delivered UNION SELECT hub_id FROM failed)
            SELECT h.hub_id, coalesce(c.n, 0) AS created, coalesce(c.fees, 0) AS booked_fees,
                   coalesce(d.n, 0) AS delivered, coalesce(f.n, 0) AS failed
            FROM hubs h
            LEFT JOIN created c ON c.hub_id = h.hub_id
            LEFT JOIN delivered d ON d.hub_id = h.hub_id
            LEFT JOIN failed f ON f.hub_id = h.hub_id
            WHERE h.hub_id IS NOT NULL
            ORDER BY coalesce(c.fees, 0) DESC
        """.trimIndent()
    }
}
