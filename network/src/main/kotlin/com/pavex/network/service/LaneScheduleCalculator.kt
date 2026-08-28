package com.pavex.network.service

import com.pavex.network.entity.LaneSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object LaneScheduleCalculator {
    val VIETNAM_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    fun nextDeparture(
        schedule: LaneSchedule,
        readyAt: Instant,
        searchDays: Int,
        accept: (ScheduledDeparture) -> Boolean = { true },
    ): ScheduledDeparture? {
        require(searchDays in 1..366) { "Schedule search horizon must be between 1 and 366 days." }
        val localReadyAt = readyAt.atZone(VIETNAM_ZONE)
        val lastDate = localReadyAt.toLocalDate().plusDays(searchDays.toLong())
        var date = maxOf(localReadyAt.toLocalDate(), schedule.effectiveFrom)

        while (!date.isAfter(lastDate)) {
            if (schedule.operatesOn(date)) {
                val departureAt = ZonedDateTime.of(date, schedule.departureTime, VIETNAM_ZONE).toInstant()
                if (!departureAt.isBefore(readyAt)) {
                    val departure = ScheduledDeparture(date, departureAt)
                    if (accept(departure)) return departure
                }
            }
            date = date.plusDays(1)
        }
        return null
    }
}

data class ScheduledDeparture(
    val serviceDate: LocalDate,
    val departureAt: Instant,
)
