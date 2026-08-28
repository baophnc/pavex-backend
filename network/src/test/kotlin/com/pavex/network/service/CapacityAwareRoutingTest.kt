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

class CapacityAwareRoutingTest {
    @Test
    fun `skips a full departure and uses the next operating date`() {
        val origin = UUID.randomUUID()
        val destination = UUID.randomUUID()
        val lane = HubLane(
            code = "HCM-HAN",
            fromHubId = origin,
            toHubId = destination,
            transportMode = TransportMode.AIR,
            estimatedTransitMinutes = 120,
            maxShipmentWeightKg = BigDecimal("30"),
            status = HubLaneStatus.ACTIVE,
        )
        val schedule = LaneSchedule.create(
            laneId = lane.id,
            departureTime = LocalTime.of(10, 0),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            effectiveFrom = LocalDate.of(2026, 8, 1),
            effectiveTo = null,
            capacityKg = BigDecimal("100"),
            active = true,
        )
        val monday = LocalDate.of(2026, 8, 31)
        val snapshot = DepartureCapacitySnapshot(
            mapOf(DepartureKey(schedule.id, monday) to BigDecimal("95")),
        )

        val path = EarliestArrivalPathFinder.find(
            originHubId = origin,
            destinationHubId = destination,
            readyAt = Instant.parse("2026-08-31T01:00:00Z"), // 08:00 VN Monday
            lanes = listOf(lane),
            schedules = listOf(schedule),
            transferBufferMinutes = 30,
            scheduleSearchDays = 7,
            shipmentWeightKg = BigDecimal("10"),
            capacity = snapshot,
        )

        requireNotNull(path)
        assertEquals(1, path.size)
        assertEquals(LocalDate.of(2026, 9, 1), path.single().serviceDate)
        assertEquals(Instant.parse("2026-09-01T03:00:00Z"), path.single().departureAt)
    }
}
