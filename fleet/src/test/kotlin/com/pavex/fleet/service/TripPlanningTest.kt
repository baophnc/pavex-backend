package com.pavex.fleet.service

import com.pavex.contracts.fleet.FleetHubSnapshot
import com.pavex.contracts.fleet.FleetRouteLegSnapshot
import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.fleet.entity.TripPoint
import com.pavex.fleet.store.StoredPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TripPlanningTest {
    private val now = Instant.parse("2026-09-29T02:00:00Z")
    private val laneA = UUID.randomUUID()
    private val laneB = UUID.randomUUID()
    private val eight = Instant.parse("2026-09-29T01:00:00Z")
    private val fourteen = Instant.parse("2026-09-29T07:00:00Z")

    @Test
    fun `shipments on the same lane departure form one trip`() {
        val legs = listOf(leg(laneA, eight), leg(laneA, eight), leg(laneA, fourteen), leg(laneB, eight))
        val assignments = legs.mapIndexed { index, leg ->
            assignment(leg.routeLegId, if (index == 1) OperationsAssignmentStatus.IN_PROGRESS else OperationsAssignmentStatus.ACCEPTED)
        }

        val departures = TripPlanner.departures(assignments, legs)

        assertEquals(3, departures.size)
        val morningA = departures.single { it.key == DepartureKey(laneA, eight) }
        assertEquals(2, morningA.assignments.size)
        assertTrue(morningA.loading)
        assertFalse(departures.single { it.key == DepartureKey(laneA, fourteen) }.loading)
        assertEquals(fourteen, departures.last().leg.plannedDepartureAt)
    }

    @Test
    fun `assignments without a known leg and other work are left out`() {
        val known = leg(laneA, eight)
        val assignments = listOf(
            assignment(known.routeLegId),
            assignment(UUID.randomUUID()),
            assignment(null),
            assignment(known.routeLegId, type = OperationsAssignmentType.LAST_MILE_DELIVERY),
        )

        val departures = TripPlanner.departures(assignments, listOf(known))

        assertEquals(1, departures.single().assignments.size)
    }

    @Test
    fun `points from the future, far past or before the trip are rejected`() {
        val startedAt = now.minusSeconds(3600)
        val points = listOf(
            point(1, now.minusSeconds(60)),
            point(1, now.minusSeconds(60)),
            point(2, now.plusSeconds(600)),
            point(3, startedAt.minusSeconds(3600)),
            point(4, startedAt.minusSeconds(300)),
        )

        val (valid, rejected) = LocationWindow.split(points, startedAt, now)

        assertEquals(listOf(1L, 4L), valid.map { it.seq })
        assertEquals(2, rejected)
    }

    @Test
    fun `a long track is thinned but keeps its last point`() {
        val points = (0 until 5_001).map { StoredPoint(now.plusSeconds(it.toLong()), 21.0, 105.0, 5.0, null, false) }

        val thinned = TripWatchService.thin(points, 2_000)

        assertTrue(thinned.size <= 2_001)
        assertEquals(points.first(), thinned.first())
        assertEquals(points.last(), thinned.last())
    }

    @Test
    fun `hub managers watch trips leaving or arriving at their hubs only`() {
        val hub = UUID.randomUUID()
        val scope = WatchScope(allHubs = false, hubIds = setOf(hub))

        assertTrue(scope.covers(hub))
        assertFalse(scope.covers(UUID.randomUUID()))
        assertTrue(WatchScope(allHubs = true, hubIds = emptySet()).covers(UUID.randomUUID()))
    }

    private fun leg(laneId: UUID, departureAt: Instant) = FleetRouteLegSnapshot(
        routeLegId = UUID.randomUUID(),
        laneId = laneId,
        laneCode = "LANE",
        scheduleId = UUID.randomUUID(),
        departureAt = departureAt,
        arrivalAt = departureAt.plusSeconds(7200),
        distanceKm = null,
        fromHub = FleetHubSnapshot(UUID.randomUUID(), "BC-HN-BADINH", "Bưu cục Ba Đình", "LOCAL"),
        toHub = FleetHubSnapshot(UUID.randomUUID(), "KHO-HN", "Kho Hà Nội", "PROVINCIAL"),
    )

    private fun assignment(
        routeLegId: UUID?,
        status: OperationsAssignmentStatus = OperationsAssignmentStatus.ACCEPTED,
        type: OperationsAssignmentType = OperationsAssignmentType.LINE_HAUL,
    ) = WorkforceAssignmentSnapshot(
        id = UUID.randomUUID(),
        code = "ASG",
        shipmentId = UUID.randomUUID(),
        sequence = 2,
        routeLegId = routeLegId,
        type = type,
        hubId = UUID.randomUUID(),
        status = status,
        priority = 50,
        assignedAt = null,
        acceptedAt = null,
        startedAt = null,
    )

    private fun point(seq: Long, at: Instant) = TripPoint(seq, at, 21.0, 105.0, 10.0)
}
