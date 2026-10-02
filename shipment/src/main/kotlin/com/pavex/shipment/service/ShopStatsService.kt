package com.pavex.shipment.service

import com.pavex.shipment.exception.InvalidShipmentRequestException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ShopDayStats(
    val date: LocalDate,
    /** Orders booked that day, and the shipping fees they were priced at. */
    val created: Long,
    val bookedFees: Long,
    val delivered: Long,
    /** Cash on delivery the recipients paid for that day's deliveries. */
    val codDelivered: Long,
    /** Orders with at least one failed delivery attempt that day. */
    val failed: Long,
    val cancelled: Long,
    val returned: Long,
)

/** Where the shop's open orders are right now, whatever day they were booked. */
data class ShopOpenOrders(
    val awaitingPickup: Long,
    val inTransit: Long,
    val outForDelivery: Long,
    val needsAttention: Long,
    val returning: Long,
    /** Cash on delivery still to be collected on the open orders. */
    val codPending: Long,
)

data class ShopStatsResponse(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<ShopDayStats>,
    val open: ShopOpenOrders,
)

/**
 * The shop's own figures for its dashboard: the same orders the shop sees in its list, i.e. the ones
 * it booked on the web. Days are Vietnamese calendar days.
 */
@Service
class ShopStatsService(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun stats(actorUserId: UUID, from: LocalDate, to: LocalDate): ShopStatsResponse {
        if (from.isAfter(to)) throw InvalidShipmentRequestException("invalid_date_range", "from must not be after to.")
        if (ChronoUnit.DAYS.between(from, to) > ShipmentStatsService.MAX_DAYS) {
            throw InvalidShipmentRequestException(
                "date_range_too_long",
                "Choose at most ${ShipmentStatsService.MAX_DAYS} days.",
            )
        }
        val params = MapSqlParameterSource()
            .addValue("actor", actorUserId)
            .addValue("start", java.sql.Timestamp.from(from.atStartOfDay(VIETNAM).toInstant()))
            .addValue("end", java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(VIETNAM).toInstant()))
            .addValue("from", java.sql.Date.valueOf(from))
            .addValue("to", java.sql.Date.valueOf(to))

        val days = jdbc.query(DAILY_SQL, params) { rs, _ ->
            ShopDayStats(
                date = rs.getDate("day").toLocalDate(),
                created = rs.getLong("created"),
                bookedFees = rs.getLong("booked_fees"),
                delivered = rs.getLong("delivered"),
                codDelivered = rs.getLong("cod_delivered"),
                failed = rs.getLong("failed"),
                cancelled = rs.getLong("cancelled"),
                returned = rs.getLong("returned"),
            )
        }
        val open = jdbc.queryForObject(OPEN_SQL, params) { rs, _ ->
            ShopOpenOrders(
                awaitingPickup = rs.getLong("awaiting_pickup"),
                inTransit = rs.getLong("in_transit"),
                outForDelivery = rs.getLong("out_for_delivery"),
                needsAttention = rs.getLong("needs_attention"),
                returning = rs.getLong("returning"),
                codPending = rs.getLong("cod_pending"),
            )
        }!!
        return ShopStatsResponse(from, to, days, open)
    }

    companion object {
        private val VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

        /** Matches ShipmentQueryService.listOwned: counter bookings by staff are not the shop's orders. */
        private const val OWNED = "s.created_by_user_id = :actor AND s.channel = 'MERCHANT_WEB'"
        private const val LOCAL_DAY = "AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"

        private val DAILY_SQL = """
            WITH days AS (
                SELECT generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day')::date AS day
            ),
            created AS (
                SELECT (s.created_at $LOCAL_DAY AS day, count(*) AS n, coalesce(sum(s.shipping_fee), 0) AS fees
                FROM shipments.shipments s
                WHERE $OWNED AND s.created_at >= :start AND s.created_at < :end
                GROUP BY 1
            ),
            delivered AS (
                SELECT (s.completed_at $LOCAL_DAY AS day, count(*) AS n, coalesce(sum(s.cod_amount), 0) AS cod
                FROM shipments.shipments s
                WHERE $OWNED AND s.status = 'DELIVERED' AND s.completed_at >= :start AND s.completed_at < :end
                GROUP BY 1
            ),
            failed AS (
                SELECT (a.occurred_at $LOCAL_DAY AS day, count(DISTINCT a.shipment_id) AS n
                FROM shipments.delivery_attempts a JOIN shipments.shipments s ON s.id = a.shipment_id
                WHERE $OWNED AND a.outcome = 'FAILED' AND a.occurred_at >= :start AND a.occurred_at < :end
                GROUP BY 1
            ),
            cancelled AS (
                SELECT (s.cancelled_at $LOCAL_DAY AS day, count(*) AS n
                FROM shipments.shipments s
                WHERE $OWNED AND s.cancelled_at >= :start AND s.cancelled_at < :end
                GROUP BY 1
            ),
            returned AS (
                SELECT (s.completed_at $LOCAL_DAY AS day, count(*) AS n
                FROM shipments.shipments s
                WHERE $OWNED AND s.status = 'RETURNED' AND s.completed_at >= :start AND s.completed_at < :end
                GROUP BY 1
            )
            SELECT d.day,
                   coalesce(c.n, 0) AS created, coalesce(c.fees, 0) AS booked_fees,
                   coalesce(dl.n, 0) AS delivered, coalesce(dl.cod, 0) AS cod_delivered,
                   coalesce(f.n, 0) AS failed, coalesce(x.n, 0) AS cancelled, coalesce(r.n, 0) AS returned
            FROM days d
            LEFT JOIN created c ON c.day = d.day
            LEFT JOIN delivered dl ON dl.day = d.day
            LEFT JOIN failed f ON f.day = d.day
            LEFT JOIN cancelled x ON x.day = d.day
            LEFT JOIN returned r ON r.day = d.day
            ORDER BY d.day
        """.trimIndent()

        private val OPEN_SQL = """
            SELECT count(*) FILTER (WHERE s.status = 'AWAITING_PICKUP') AS awaiting_pickup,
                   count(*) FILTER (WHERE s.status IN ('PICKED_UP', 'AT_ORIGIN_HUB', 'IN_TRANSIT', 'AT_DESTINATION_HUB')) AS in_transit,
                   count(*) FILTER (WHERE s.status = 'OUT_FOR_DELIVERY') AS out_for_delivery,
                   count(*) FILTER (WHERE s.status IN ('DELIVERY_FAILED', 'PARTIALLY_DELIVERED')) AS needs_attention,
                   count(*) FILTER (WHERE s.status = 'RETURNING') AS returning,
                   coalesce(sum(s.cod_amount) FILTER (WHERE s.status <> 'RETURNING'), 0) AS cod_pending
            FROM shipments.shipments s
            WHERE $OWNED AND s.lifecycle_status = 'ACTIVE'
        """.trimIndent()
    }
}
