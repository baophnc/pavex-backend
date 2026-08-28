package com.pavex.network.service

import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.LaneSchedule
import com.pavex.network.entity.TransportMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class EarliestArrivalPathFinderTest {
    @Test
    fun `chooses earlier arrival instead of smallest transit-minute sum`() {
        val origin = UUID.randomUUID()
        val transfer = UUID.randomUUID()
        val destination = UUID.randomUUID()

        val direct = lane("DIRECT", origin, destination, 60)
        val first = lane("FIRST", origin, transfer, 120)
        val second = lane("SECOND", transfer, destination, 180)

        val schedules = listOf(
            schedule(direct.id, LocalTime.of(20, 0), setOf(DayOfWeek.TUESDAY)),
            schedule(first.id, LocalTime.of(9, 0), setOf(DayOfWeek.MONDAY)),
            schedule(second.id, LocalTime.of(12, 0), setOf(DayOfWeek.MONDAY)),
        )
        val readyAt = Instant.parse("2026-08-31T01:00:00Z") // Monday 08:00 VN

        val path = EarliestArrivalPathFinder.find(
            originHubId = origin,
            destinationHubId = destination,
            readyAt = readyAt,
            lanes = listOf(direct, first, second),
            schedules = schedules,
            transferBufferMinutes = 30,
            scheduleSearchDays = 7,
        )

        requireNotNull(path)
        assertEquals(listOf("FIRST", "SECOND"), path.map { it.lane.code })
        assertEquals(Instant.parse("2026-08-31T08:00:00Z"), path.last().arrivalAt) // 15:00 VN
    }

    private fun lane(code: String, from: UUID, to: UUID, transitMinutes: Int) = HubLane(
        code = code,
        fromHubId = from,
        toHubId = to,
        transportMode = TransportMode.ROAD,
        estimatedTransitMinutes = transitMinutes,
        maxShipmentWeightKg = BigDecimal("1000"),
        status = HubLaneStatus.ACTIVE,
    )

    private fun schedule(laneId: UUID, time: LocalTime, days: Set<DayOfWeek>) = LaneSchedule.create(
        laneId = laneId,
        departureTime = time,
        daysOfWeek = days,
        effectiveFrom = LocalDate.of(2026, 8, 1),
        effectiveTo = null,
        active = true,
    )
}
