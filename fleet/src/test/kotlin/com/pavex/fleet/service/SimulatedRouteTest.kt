package com.pavex.fleet.service

import com.pavex.fleet.entity.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SimulatedRouteTest {
    private val saigon = GeoPoint(10.8013, 106.6522)
    private val hanoi = GeoPoint(21.0355, 105.8270)
    private val start = Instant.parse("2026-10-02T03:00:00Z")

    @Test
    fun `a truck covers 10 km every 10 minutes at 60 kmh`() {
        val position = SimulatedRoute.position(saigon, hanoi, 1700.0, 60.0, start, start.plus(Duration.ofMinutes(10)))

        assertEquals(10_000.0, position.travelledMeters, 1.0)
        assertFalse(position.arrived)
        // Heading north, a little west.
        assertTrue(position.headingDeg > 350 || position.headingDeg < 10)
        assertTrue(position.point.lat > saigon.lat && position.point.lat < hanoi.lat)
    }

    @Test
    fun `the truck stops at the destination hub`() {
        val position = SimulatedRoute.position(saigon, hanoi, 1700.0, 60.0, start, start.plus(Duration.ofHours(40)))

        assertTrue(position.arrived)
        assertEquals(1_700_000.0, position.travelledMeters, 1.0)
        assertEquals(hanoi, position.point)
    }

    @Test
    fun `without a lane distance the road is a quarter longer than the straight line`() {
        val position = SimulatedRoute.position(saigon, hanoi, null, 60.0, start, start.plus(Duration.ofHours(100)))

        assertTrue(position.arrived)
        assertTrue(position.travelledMeters > 1_400_000 && position.travelledMeters < 1_500_000)
    }
}
