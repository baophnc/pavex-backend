package com.pavex.fleet.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class FleetTripTest {
    private val start = Instant.parse("2026-09-29T01:00:00Z")

    @Test
    fun `positions move the truck and add up the distance`() {
        val trip = trip()

        // Three fixes roughly 1.1 km apart along a meridian, one minute each.
        trip.applyPoints(listOf(point(1, 0, 21.0000), point(2, 60, 21.0100), point(3, 120, 21.0200)), start.plusSeconds(130))

        assertEquals(3, trip.pointCount)
        assertEquals(21.02, trip.lastLat)
        assertEquals(start.plusSeconds(120), trip.lastRecordedAt)
        assertEquals(2224.0, trip.travelledMeters, 5.0)
    }

    @Test
    fun `a late resend is stored but does not pull the truck back`() {
        val trip = trip()
        trip.applyPoints(listOf(point(1, 0, 21.0), point(2, 60, 21.01)), start.plusSeconds(70))
        val travelled = trip.travelledMeters

        trip.applyPoints(listOf(point(0, -30, 20.99)), start.plusSeconds(80))

        assertEquals(3, trip.pointCount)
        assertEquals(21.01, trip.lastLat)
        assertEquals(travelled, trip.travelledMeters)
    }

    @Test
    fun `an impossible jump neither moves the truck nor adds distance`() {
        val trip = trip()
        trip.applyPoints(listOf(point(1, 0, 21.0)), start)
        // 11 km in ten seconds is a glitch.
        trip.applyPoints(listOf(point(2, 10, 21.1)), start.plusSeconds(10))

        assertEquals(0.0, trip.travelledMeters)
        assertEquals(21.0, trip.lastLat)
        assertEquals(2, trip.pointCount)
    }

    @Test
    fun `a vague fix moves the truck but does not count as distance`() {
        val trip = trip()
        trip.applyPoints(listOf(point(1, 0, 21.0)), start)
        trip.applyPoints(listOf(point(2, 600, 21.01, accuracy = 500.0)), start.plusSeconds(600))

        assertEquals(0.0, trip.travelledMeters)
        assertEquals(21.01, trip.lastLat)
    }

    @Test
    fun `a trip arrives once`() {
        val trip = trip()
        trip.arrive(TripEndSource.ALL_RECEIVED, start.plusSeconds(3600))

        assertFalse(trip.inProgress)
        assertEquals(TripEndSource.ALL_RECEIVED, trip.endSource)
        assertThrows(IllegalStateException::class.java) { trip.arrive(TripEndSource.DRIVER, start.plusSeconds(3700)) }
    }

    @Test
    fun `vehicle plates are normalised and checked`() {
        assertEquals("51C-123.45", FleetTrip.normalizePlate(" 51c-123.45 "))
        assertNull(FleetTrip.normalizePlate("  "))
        assertThrows(IllegalArgumentException::class.java) { FleetTrip.normalizePlate("51C/123") }
    }

    @Test
    fun `distance between Hanoi and Hai Phong is about 100 km`() {
        val meters = GeoDistance.meters(21.0285, 105.8542, 20.8449, 106.6881)
        assertTrue(meters in 88_000.0..92_000.0, "was $meters")
    }

    private fun trip() = FleetTrip.start(
        driverUserId = UUID.randomUUID(),
        driverEmail = "taixe.hn.badinh@pavex.com",
        leg = TripLeg(
            laneId = UUID.randomUUID(),
            laneCode = "BC-HN-BADINH>KHO-HN",
            fromHubId = UUID.randomUUID(),
            fromHubCode = "BC-HN-BADINH",
            fromHubName = "Bưu cục Ba Đình",
            toHubId = UUID.randomUUID(),
            toHubCode = "KHO-HN",
            toHubName = "Kho Hà Nội",
            plannedDepartureAt = start,
            plannedArrivalAt = start.plusSeconds(7200),
            distanceKm = BigDecimal("12.50"),
        ),
        vehiclePlate = null,
        source = TripStartSource.DRIVER,
        at = start,
    )

    private fun point(seq: Long, secondsAfterStart: Long, lat: Double, accuracy: Double = 10.0) = TripPoint(
        seq = seq,
        recordedAt = start.plusSeconds(secondsAfterStart),
        lat = lat,
        lng = 105.85,
        accuracyM = accuracy,
    )
}
