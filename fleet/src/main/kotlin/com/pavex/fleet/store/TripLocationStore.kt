package com.pavex.fleet.store

import com.pavex.fleet.config.FleetTrackingProperties
import com.pavex.fleet.entity.TripPoint
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.sql.Types
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A stored GPS point as the track view reads it back. */
data class StoredPoint(
    val recordedAt: Instant,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val speedMps: Double?,
    val mocked: Boolean,
)

/**
 * Raw GPS points live outside JPA: they are written in batches and the table is partitioned by UTC
 * day, so old days are dropped as whole partitions instead of being deleted row by row.
 */
@Component
class TripLocationStore(
    private val jdbc: JdbcTemplate,
) {
    /** Inserts the points and returns the ones that were new; resends of a (trip, seq, time) are skipped. */
    fun insert(tripId: UUID, points: List<TripPoint>, receivedAt: Instant): List<TripPoint> {
        if (points.isEmpty()) return emptyList()
        val counts = jdbc.batchUpdate(INSERT, points, points.size) { statement, point ->
            statement.setObject(1, tripId)
            statement.setLong(2, point.seq)
            statement.setTimestamp(3, Timestamp.from(point.recordedAt))
            statement.setTimestamp(4, Timestamp.from(receivedAt))
            statement.setDouble(5, point.lat)
            statement.setDouble(6, point.lng)
            statement.setFloat(7, point.accuracyM.toFloat())
            statement.setNullableFloat(8, point.speedMps)
            statement.setNullableFloat(9, point.headingDeg)
            if (point.batteryPct == null) statement.setNull(10, Types.SMALLINT) else statement.setShort(10, point.batteryPct.toShort())
            statement.setBoolean(11, point.mocked)
        }
        val inserted = counts.flatMap { it.asList() }
        return points.filterIndexed { index, _ -> inserted.getOrElse(index) { 0 } != 0 }
    }

    /** The track of a trip in time order, at most [limit] points. */
    fun track(tripId: UUID, limit: Int): List<StoredPoint> =
        jdbc.query(
            """
            SELECT recorded_at, lat, lng, accuracy_m, speed_mps, mocked
            FROM fleet.trip_locations
            WHERE trip_id = ?
            ORDER BY recorded_at
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                StoredPoint(
                    recordedAt = rs.getTimestamp("recorded_at").toInstant(),
                    lat = rs.getDouble("lat"),
                    lng = rs.getDouble("lng"),
                    accuracyM = rs.getDouble("accuracy_m"),
                    speedMps = rs.getObject("speed_mps")?.let { (it as Number).toDouble() },
                    mocked = rs.getBoolean("mocked"),
                )
            },
            tripId,
            limit,
        )

    private fun java.sql.PreparedStatement.setNullableFloat(index: Int, value: Double?) {
        if (value == null) setNull(index, Types.REAL) else setFloat(index, value.toFloat())
    }

    companion object {
        private val INSERT = """
            INSERT INTO fleet.trip_locations
                (trip_id, seq, recorded_at, received_at, lat, lng, accuracy_m, speed_mps, heading_deg, battery_pct, mocked)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
        """.trimIndent()
    }
}

/** Keeps a daily partition ready from yesterday to two days ahead, and drops days past retention. */
@Component
class TripLocationPartitions(
    private val jdbc: JdbcTemplate,
    private val properties: FleetTrackingProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelay = 0, fixedDelayString = "PT1H")
    fun maintain() {
        val today = LocalDate.now(clock.withZone(ZoneOffset.UTC))
        (-1L..2L).forEach { create(today.plusDays(it)) }
        dropBefore(today.minusDays(properties.retentionDays.toLong()))
    }

    private fun create(day: LocalDate) {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS fleet.${partitionName(day)} PARTITION OF fleet.trip_locations " +
                    "FOR VALUES FROM ('$day 00:00:00+00') TO ('${day.plusDays(1)} 00:00:00+00')",
            )
        } catch (exception: DataAccessException) {
            // Points for that day already sit in the default partition; they stay readable there.
            log.warn("Could not create GPS partition for {}: {}", day, exception.mostSpecificCause.message)
        }
    }

    private fun dropBefore(oldestKept: LocalDate) {
        val partitions = jdbc.queryForList(
            """
            SELECT child.relname
            FROM pg_inherits
            JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
            JOIN pg_class child ON child.oid = pg_inherits.inhrelid
            JOIN pg_namespace ns ON ns.oid = parent.relnamespace
            WHERE ns.nspname = 'fleet' AND parent.relname = 'trip_locations'
            """.trimIndent(),
            String::class.java,
        )
        partitions.filterNotNull().mapNotNull { name -> dayOf(name)?.let { name to it } }
            .filter { (_, day) -> day.isBefore(oldestKept) }
            .forEach { (name, _) -> jdbc.execute("DROP TABLE IF EXISTS fleet.$name") }
        jdbc.update(
            "DELETE FROM fleet.trip_locations_default WHERE recorded_at < ?",
            Timestamp.from(oldestKept.atStartOfDay(ZoneOffset.UTC).toInstant()),
        )
    }

    companion object {
        private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val NAME = Regex("^trip_locations_p(\\d{8})$")

        fun partitionName(day: LocalDate) = "trip_locations_p${day.format(DAY)}"

        fun dayOf(partition: String): LocalDate? =
            NAME.find(partition)?.groupValues?.get(1)?.let { LocalDate.parse(it, DAY) }
    }
}
