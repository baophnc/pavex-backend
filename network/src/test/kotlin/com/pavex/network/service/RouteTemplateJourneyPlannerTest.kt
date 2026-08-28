package com.pavex.network.service

import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.LaneSchedule
import com.pavex.network.entity.TransportMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class RouteTemplateJourneyPlannerTest {
    @Test
    fun `plans fixed template legs using the next available departure for each lane`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        val first = lane("A-B", a, b, 120)
        val second = lane("B-C", b, c, 180)
        val monday = LocalDate.of(2026, 8, 31)
        val schedules = listOf(
            schedule(first.id, LocalTime.of(9, 0), monday),
            schedule(second.id, LocalTime.of(12, 0), monday),
        )

        val result = RouteTemplateJourneyPlanner.plan(
            readyAt = Instant.parse("2026-08-31T01:00:00Z"), // 08:00 Vietnam
            lanes = listOf(first, second),
            schedules = schedules,
            transferBufferMinutes = 30,
            scheduleSearchDays = 7,
        )

        requireNotNull(result)
        assertEquals(listOf("A-B", "B-C"), result.map { it.lane.code })
        assertEquals(Instant.parse("2026-08-31T02:00:00Z"), result[0].departureAt)
        assertEquals(Instant.parse("2026-08-31T04:00:00Z"), result[0].arrivalAt)
        assertEquals(Instant.parse("2026-08-31T05:00:00Z"), result[1].departureAt)
        assertEquals(Instant.parse("2026-08-31T08:00:00Z"), result[1].arrivalAt)
    }

    @Test
    fun `returns null when any fixed template leg has no usable departure`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        val first = lane("A-B", a, b, 60)
        val second = lane("B-C", b, c, 60)
        val monday = LocalDate.of(2026, 8, 31)

        val result = RouteTemplateJourneyPlanner.plan(
            readyAt = Instant.parse("2026-08-31T01:00:00Z"),
            lanes = listOf(first, second),
            schedules = listOf(schedule(first.id, LocalTime.of(9, 0), monday)),
            transferBufferMinutes = 30,
            scheduleSearchDays = 7,
        )

        assertNull(result)
    }

    private fun lane(code: String, from: UUID, to: UUID, transitMinutes: Int) = HubLane(
        code = code,
        fromHubId = from,
        toHubId = to,
        transportMode = TransportMode.ROAD,
        estimatedTransitMinutes = transitMinutes,
        maxShipmentWeightKg = BigDecimal("100"),
        status = HubLaneStatus.ACTIVE,
    )

    private fun schedule(laneId: UUID, departure: LocalTime, effectiveFrom: LocalDate) =
        LaneSchedule.create(
            laneId = laneId,
            departureTime = departure,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            effectiveFrom = effectiveFrom,
            effectiveTo = null,
            active = true,
        )
}
