package com.pavex.network.service

import com.pavex.network.entity.LaneSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class LaneScheduleCalculatorTest {
    @Test
    fun `uses the next departure on the same service day`() {
        val schedule = LaneSchedule.create(
            laneId = UUID.randomUUID(),
            departureTime = LocalTime.of(10, 30),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            effectiveFrom = LocalDate.of(2026, 8, 1),
            effectiveTo = null,
            active = true,
        )

        val result = LaneScheduleCalculator.nextDeparture(
            schedule,
            Instant.parse("2026-08-31T02:00:00Z"), // 09:00 in Vietnam
            14,
        )

        requireNotNull(result)
        assertEquals(LocalDate.of(2026, 8, 31), result.serviceDate)
        assertEquals(Instant.parse("2026-08-31T03:30:00Z"), result.departureAt)
    }

    @Test
    fun `returns null when no departure exists inside the search horizon`() {
        val schedule = LaneSchedule.create(
            laneId = UUID.randomUUID(),
            departureTime = LocalTime.NOON,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            effectiveFrom = LocalDate.of(2026, 9, 28),
            effectiveTo = null,
            active = true,
        )

        val result = LaneScheduleCalculator.nextDeparture(
            schedule,
            Instant.parse("2026-08-31T01:00:00Z"),
            14,
        )

        assertNull(result)
    }
}
