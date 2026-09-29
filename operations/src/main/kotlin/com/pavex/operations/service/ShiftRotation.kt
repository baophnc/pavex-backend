package com.pavex.operations.service

import com.pavex.operations.entity.WorkforceMemberKind
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.IsoFields
import java.util.UUID

/** A daily shift a hub runs, in Vietnam local time. */
data class ShiftSlot(val index: Int, val name: String, val start: LocalTime, val length: Duration)

/**
 * The standing weekly roster: who works which shift on which day, without anyone editing it.
 *
 * - Small post offices open one office-hours shift; larger ones run a morning and an afternoon
 *   shift; provincial warehouses and regional centres run three shifts around the clock.
 * - Line-haul drivers are rostered apart from the hub staff, on the two daytime shifts only.
 * - Everyone works six days and has one day off. Members are ordered by id, member `i` rests on
 *   weekday `i % 7` and works shift `(i + week) % shifts`, so the shifts rotate every ISO week.
 * - Because 7 and the shift count share no divisor, the members of one shift all rest on
 *   different days; with at least two per shift, every shift of every day has someone.
 */
object ShiftRotation {
    private val OFFICE_HOURS = listOf(ShiftSlot(0, "Ca hành chính", LocalTime.of(7, 30), Duration.ofHours(10)))
    private val POST_OFFICE = listOf(
        ShiftSlot(0, "Ca sáng", LocalTime.of(7, 0), Duration.ofHours(8)),
        ShiftSlot(1, "Ca chiều", LocalTime.of(13, 0), Duration.ofHours(8)),
    )
    private val ROUND_THE_CLOCK = listOf(
        ShiftSlot(0, "Ca sáng", LocalTime.of(6, 0), Duration.ofHours(8)),
        ShiftSlot(1, "Ca chiều", LocalTime.of(14, 0), Duration.ofHours(8)),
        ShiftSlot(2, "Ca đêm", LocalTime.of(22, 0), Duration.ofHours(8)),
    )

    /** Post offices with fewer members than this open a single office-hours shift. */
    const val TWO_SHIFT_POST_OFFICE_MIN_MEMBERS = 5

    fun slots(hubType: String, memberCount: Int, drivers: Boolean = false): List<ShiftSlot> = when {
        memberCount <= 0 -> emptyList()
        drivers -> if (memberCount >= 4) ROUND_THE_CLOCK.take(2) else OFFICE_HOURS
        hubType == "LOCAL" -> if (memberCount >= TWO_SHIFT_POST_OFFICE_MIN_MEMBERS) POST_OFFICE else OFFICE_HOURS
        memberCount >= 6 -> ROUND_THE_CLOCK
        memberCount >= 4 -> ROUND_THE_CLOCK.take(2)
        else -> OFFICE_HOURS
    }

    fun dayOff(position: Int): DayOfWeek = DayOfWeek.of(position % 7 + 1)

    fun week(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** Members on each shift of [date]; [members] must be the hub's roster in a stable order. */
    fun plan(members: List<UUID>, shiftCount: Int, date: LocalDate): Map<Int, List<UUID>> {
        if (shiftCount <= 0) return emptyMap()
        val week = week(date)
        return members.withIndex()
            .filter { (position, _) -> dayOff(position) != date.dayOfWeek }
            .groupBy({ (position, _) -> (position + week) % shiftCount }, { it.value })
    }

    /** The stable roster order: by the text form of the id, so tools outside the JVM agree. */
    fun ordered(members: Collection<UUID>): List<UUID> = members.sortedBy(UUID::toString)
}

/**
 * Where a checked-in worker can take jobs. Hub staff work at the hub of their shift. A line-haul
 * driver checks in at the fleet's home warehouse and also runs the post offices on their route,
 * so their shift covers every hub they are a member of.
 */
object ShiftCoverage {
    fun covers(kind: WorkforceMemberKind, jobHubId: UUID, shiftHubId: UUID, memberHubIds: Collection<UUID>): Boolean =
        shiftHubId == jobHubId || (kind == WorkforceMemberKind.LINE_HAUL_DRIVER && jobHubId in memberHubIds && shiftHubId in memberHubIds)
}
