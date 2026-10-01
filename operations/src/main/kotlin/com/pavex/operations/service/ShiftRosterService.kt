package com.pavex.operations.service

import com.pavex.operations.client.NetworkHubClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@ConfigurationProperties("operations.shifts.roster")
data class ShiftRosterProperties(
    val enabled: Boolean = true,
    /** Days of shifts kept ready, starting today. */
    val daysAhead: Int = 7,
    val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh"),
    /** Recorded as the creator of rostered shifts: the bootstrap administrator. */
    val actorUserId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    /** A check-in still open this long after its shift ended is checked out automatically. */
    val autoCheckOutAfter: Duration = Duration.ofHours(1),
    /**
     * For testing: one shift from 00:00 to 24:00 every day for everyone, no day off, so staff can
     * check in and take jobs at any hour.
     */
    val fullDay: Boolean = false,
)

@Configuration
@EnableConfigurationProperties(ShiftRosterProperties::class)
class ShiftRosterConfig

data class RosterResult(val shiftsCreated: Int, val rosterEntriesCreated: Int, val checkedOut: Int)

/**
 * Keeps every hub's shifts filled for the coming week from the standing rotation, so nobody has to
 * create shifts or roster staff by hand. It only adds what is missing: shifts that exist at the same
 * hub and start time are reused, and people already on a shift stay as they are.
 */
@Service
class ShiftRosterService(
    private val jdbc: JdbcTemplate,
    private val network: NetworkHubClient,
    private val properties: ShiftRosterProperties,
    private val transactions: TransactionTemplate,
    private val dispatchQueue: AssignmentDispatchQueue,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "\${operations.shifts.roster.initial-delay:PT20S}", fixedDelayString = "\${operations.shifts.roster.interval:PT1H}")
    fun scheduled() {
        if (!properties.enabled) return
        try {
            val result = run(clock.instant())
            if (result != RosterResult(0, 0, 0)) log.info("Shift roster updated: {}", result)
        } catch (exception: Exception) {
            // Network may still be starting; the next run catches up.
            log.warn("Shift roster run skipped: {}", exception.message)
        }
    }

    fun run(now: Instant): RosterResult {
        // Before ended shifts are closed: with full-day shifts, whoever worked through midnight goes on.
        if (properties.fullDay) {
            val carried = transactions.execute { carryOverFullDay(now) } ?: 0
            if (carried > 0) log.info("Full-day shifts: {} people carried over to today's shift", carried)
        }
        val checkedOut = transactions.execute { closeEndedShifts(now) } ?: 0
        val (shifts, entries) = transactions.execute { roster(now) } ?: (0 to 0)
        return RosterResult(shifts, entries, checkedOut)
    }

    /**
     * Testing with full-day shifts: someone still checked in when their day's shift ended is moved
     * to today's shift at the same hub, so jobs keep reaching them after midnight.
     */
    private fun carryOverFullDay(now: Instant): Int {
        val members = jdbc.queryForList(
            """
            WITH moving AS (
                SELECT old.id AS old_id, cur.id AS new_id, old.workforce_member_id, s.ends_at AS ended_at
                FROM operations.workforce_shift_assignments old
                JOIN operations.work_shifts s ON s.id = old.shift_id
                JOIN operations.workforce_shift_assignments cur
                  ON cur.workforce_member_id = old.workforce_member_id AND cur.status = 'SCHEDULED'
                JOIN operations.work_shifts cs
                  ON cs.id = cur.shift_id AND cs.hub_id = s.hub_id AND cs.status = 'SCHEDULED'
                 AND cs.starts_at <= ? AND cs.ends_at > ?
                WHERE old.status = 'CHECKED_IN' AND s.ends_at <= ?
            ), ended AS (
                UPDATE operations.workforce_shift_assignments a
                SET status = 'CHECKED_OUT', checked_out_at = m.ended_at, status_changed_by_user_id = ?,
                    note = COALESCE(a.note, 'Chuyển sang ca hôm sau'), updated_at = ?, version = a.version + 1
                FROM moving m WHERE a.id = m.old_id
                RETURNING a.id
            )
            UPDATE operations.workforce_shift_assignments a
            SET status = 'CHECKED_IN', checked_in_at = ?, status_changed_by_user_id = ?, updated_at = ?, version = a.version + 1
            FROM moving m WHERE a.id = m.new_id
            RETURNING a.workforce_member_id
            """.trimIndent(),
            UUID::class.java,
            Timestamp.from(now), Timestamp.from(now), Timestamp.from(now),
            properties.actorUserId, Timestamp.from(now),
            Timestamp.from(now), properties.actorUserId, Timestamp.from(now),
        ).filterNotNull()
        members.forEach { dispatchQueue.wake(it) }
        return members.size
    }

    /** Checks out people who forgot to, so they can check in to their next shift. */
    private fun closeEndedShifts(now: Instant): Int {
        val members = jdbc.queryForList(
            """
            UPDATE operations.workforce_shift_assignments a
            SET status = 'CHECKED_OUT', checked_out_at = s.ends_at, status_changed_by_user_id = ?,
                note = COALESCE(a.note, 'Tự check-out sau khi ca kết thúc'), updated_at = ?, version = a.version + 1
            FROM operations.work_shifts s
            WHERE s.id = a.shift_id AND a.status = 'CHECKED_IN' AND s.ends_at < ?
            RETURNING a.workforce_member_id
            """.trimIndent(),
            UUID::class.java,
            properties.actorUserId,
            Timestamp.from(now),
            Timestamp.from(now.minus(properties.autoCheckOutAfter)),
        ).filterNotNull()
        if (members.isEmpty()) return 0
        // Off duty unless still checked in elsewhere; the entries closed above carry this run's timestamp.
        jdbc.update(
            """
            UPDATE operations.workforce_availabilities v
            SET status = 'OFF_DUTY', updated_at = ?, version = v.version + 1
            WHERE v.status = 'AVAILABLE'
              AND EXISTS (
                  SELECT 1 FROM operations.workforce_shift_assignments a
                  WHERE a.workforce_member_id = v.workforce_member_id AND a.status = 'CHECKED_OUT' AND a.updated_at = ?
              )
              AND NOT EXISTS (
                  SELECT 1 FROM operations.workforce_shift_assignments a
                  WHERE a.workforce_member_id = v.workforce_member_id AND a.status = 'CHECKED_IN'
              )
            """.trimIndent(),
            Timestamp.from(now),
            Timestamp.from(now),
        )
        dispatchQueue.releaseUnaccepted(members, now)
        return members.size
    }

    private fun roster(now: Instant): Pair<Int, Int> {
        val zone = properties.zone
        val today = LocalDate.ofInstant(now, zone)
        val days = (0L until properties.daysAhead.toLong()).map(today::plusDays)
        // Each hub's staff and its drivers are two rosters: drivers only work the daytime shifts.
        val rosters = jdbc.query(
            """
            SELECT m.hub_id, w.id, w.kind = 'LINE_HAUL_DRIVER'
            FROM operations.hub_memberships m
            JOIN operations.workforce_members w ON w.id = m.workforce_member_id
            WHERE m.is_active AND m.is_primary AND w.status = 'ACTIVE'
            """.trimIndent(),
        ) { rs, _ -> Triple(rs.getObject(1, UUID::class.java), rs.getBoolean(3), rs.getObject(2, UUID::class.java)) }
            .groupBy({ it.first to it.second }, { it.third })
            .mapValues { (_, members) -> ShiftRotation.ordered(members) }
        if (rosters.isEmpty()) return 0 to 0
        val hubTypes = network.snapshots(rosters.keys.map { it.first })
            .filter { it.status == "ACTIVE" }
            .associate { it.hubId to it.type }

        val windowStart = days.first().atStartOfDay(zone).toInstant()
        val windowEnd = days.last().plusDays(1).atStartOfDay(zone).toInstant()
        val existingShifts = jdbc.query(
            "SELECT hub_id, starts_at, id FROM operations.work_shifts WHERE starts_at >= ? AND starts_at < ? AND status = 'SCHEDULED'",
            { rs, _ -> (rs.getObject(1, UUID::class.java) to rs.getTimestamp(2).toInstant()) to rs.getObject(3, UUID::class.java) },
            Timestamp.from(windowStart),
            Timestamp.from(windowEnd),
        ).toMap(HashMap())
        val existingEntries = jdbc.query(
            """
            SELECT a.shift_id, a.workforce_member_id
            FROM operations.workforce_shift_assignments a
            JOIN operations.work_shifts s ON s.id = a.shift_id
            WHERE s.starts_at >= ? AND s.starts_at < ?
            """.trimIndent(),
            { rs, _ -> rs.getObject(1, UUID::class.java) to rs.getObject(2, UUID::class.java) },
            Timestamp.from(windowStart),
            Timestamp.from(windowEnd),
        ).toHashSet()

        val newShifts = mutableListOf<Array<Any?>>()
        val newEntries = mutableListOf<Array<Any?>>()
        val stamp = Timestamp.from(now)
        for ((key, members) in rosters) {
            val (hubId, drivers) = key
            val hubType = hubTypes[hubId] ?: continue
            val slots = if (properties.fullDay) listOf(ShiftRotation.FULL_DAY) else ShiftRotation.slots(hubType, members.size, drivers)
            for (day in days) {
                val plan = if (properties.fullDay) mapOf(0 to members) else ShiftRotation.plan(members, slots.size, day)
                for (slot in slots) {
                    val startsAt = day.atTime(slot.start).atZone(zone).toInstant()
                    val endsAt = startsAt.plus(slot.length)
                    if (!endsAt.isAfter(now)) continue
                    val shiftId = existingShifts[hubId to startsAt] ?: rosterId("shift", hubId, startsAt).also { id ->
                        existingShifts[hubId to startsAt] = id
                        newShifts += arrayOf(
                            id, hubId, "PAVEX-SH-" + id.toString().replace("-", "").uppercase().take(11),
                            "${slot.name} ${day.format(DAY)}", Timestamp.from(startsAt), Timestamp.from(endsAt),
                            properties.actorUserId, stamp, stamp,
                        )
                    }
                    for (member in plan[slot.index].orEmpty()) {
                        if ((shiftId to member) in existingEntries) continue
                        newEntries += arrayOf(rosterId("entry", shiftId, member), shiftId, member, properties.actorUserId, properties.actorUserId, stamp, stamp)
                    }
                }
            }
        }

        jdbc.batchUpdate(
            """
            INSERT INTO operations.work_shifts (id, hub_id, code, name, starts_at, ends_at, status, created_by_user_id, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, ?, ?, 'SCHEDULED', ?, ?, ?, 0)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            newShifts,
        )
        jdbc.batchUpdate(
            """
            INSERT INTO operations.workforce_shift_assignments
                (id, shift_id, workforce_member_id, status, assigned_by_user_id, status_changed_by_user_id, assigned_at, updated_at, version)
            VALUES (?, ?, ?, 'SCHEDULED', ?, ?, ?, ?, 0)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            newEntries,
        )
        return newShifts.size to newEntries.size
    }

    companion object {
        private val DAY = DateTimeFormatter.ofPattern("dd/MM")

        /** Same inputs, same id: running twice cannot create a second shift or roster entry. */
        fun rosterId(kind: String, first: UUID, second: Any): UUID =
            UUID.nameUUIDFromBytes("pavex-roster:$kind:$first:$second".toByteArray())
    }
}
