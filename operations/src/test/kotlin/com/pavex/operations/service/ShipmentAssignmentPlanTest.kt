package com.pavex.operations.service

import com.pavex.operations.dto.ShipmentAssignmentPlan
import com.pavex.operations.dto.ShipmentAssignmentRouteLeg
import com.pavex.operations.entity.OperationalAssignmentType
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.OperationalAssignmentEventLogRepository
import com.pavex.operations.repository.OperationalAssignmentRepository
import com.pavex.operations.repository.WorkShiftRepository
import com.pavex.operations.repository.WorkforceAvailabilityRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import com.pavex.operations.repository.WorkforceShiftAssignmentRepository
import com.pavex.operations.client.IdentityWorkforceClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ShipmentAssignmentPlanTest {
    private val origin = UUID.randomUUID()
    private val transit = UUID.randomUUID()
    private val destination = UUID.randomUUID()
    private val service = OperationalAssignmentService(
        mock(OperationalAssignmentRepository::class.java),
        mock(OperationalAssignmentEventLogRepository::class.java),
        mock(WorkforceMemberRepository::class.java),
        mock(HubMembershipRepository::class.java),
        mock(WorkforceAvailabilityRepository::class.java),
        mock(WorkforceShiftAssignmentRepository::class.java),
        mock(WorkShiftRepository::class.java),
        mock(IdentityWorkforceClient::class.java),
        mock(AssignmentLockService::class.java),
        mock(AssignmentDispatchQueue::class.java),
        Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `a shop shipment starts with the courier pickup`() {
        val drafts = service.buildDrafts(plan(startsAtOriginHub = false))

        assertEquals(
            listOf(
                OperationalAssignmentType.PICKUP,
                OperationalAssignmentType.ORIGIN_HUB_HANDLING,
                OperationalAssignmentType.LINE_HAUL,
                OperationalAssignmentType.TRANSIT_HUB_HANDLING,
                OperationalAssignmentType.LINE_HAUL,
                OperationalAssignmentType.DESTINATION_HUB_HANDLING,
                OperationalAssignmentType.LAST_MILE_DELIVERY,
            ),
            drafts.map { it.type },
        )
    }

    @Test
    fun `a counter shipment skips the pickup and starts with the origin hub`() {
        val drafts = service.buildDrafts(plan(startsAtOriginHub = true))

        assertEquals(OperationalAssignmentType.ORIGIN_HUB_HANDLING, drafts.first().type)
        assertEquals(origin, drafts.first().hubId)
        assertEquals(drafts.indices.map { it + 1 }, drafts.map { it.sequence })
        assertEquals(0, drafts.count { it.type == OperationalAssignmentType.PICKUP })
    }

    private fun plan(startsAtOriginHub: Boolean) = ShipmentAssignmentPlan(
        shipmentId = UUID.randomUUID(),
        originHubId = origin,
        destinationHubId = destination,
        routeLegs = listOf(
            ShipmentAssignmentRouteLeg(UUID.randomUUID(), 1, origin, transit),
            ShipmentAssignmentRouteLeg(UUID.randomUUID(), 2, transit, destination),
        ),
        startsAtOriginHub = startsAtOriginHub,
    )
}
