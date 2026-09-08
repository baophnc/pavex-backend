package com.pavex.operations.service

import com.pavex.operations.entity.OperationalAssignmentEventLog
import com.pavex.operations.entity.OperationalAssignmentEventType
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.repository.OperationalAssignmentEventLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.domain.PageImpl
import java.time.Instant
import java.util.UUID

class OperationalAssignmentEventQueryServiceTest {
    @Test
    fun `search clamps page size and maps immutable event history`() {
        val event = OperationalAssignmentEventLog(
            assignmentId = UUID.randomUUID(),
            shipmentId = UUID.randomUUID(),
            eventType = OperationalAssignmentEventType.REJECTED,
            previousStatus = OperationalAssignmentStatus.ASSIGNED,
            newStatus = OperationalAssignmentStatus.REJECTED,
            workforceMemberId = UUID.randomUUID(),
            actorUserId = UUID.randomUUID(),
            reason = "vehicle issue",
            occurredAt = Instant.parse("2026-08-31T08:00:00Z"),
        )
        val repository = mock(OperationalAssignmentEventLogRepository::class.java) { invocation ->
            if (invocation.method.name == "search") PageImpl(listOf(event)) else null
        }
        val service = OperationalAssignmentEventQueryService(repository)

        val page = service.search(
            AssignmentAccessScope.global(UUID.randomUUID()),
            assignmentId = null,
            shipmentId = null,
            eventType = null,
            page = 0,
            pageSize = 999,
        )

        assertEquals(1, page.page)
        assertEquals(200, page.pageSize)
        assertEquals(1, page.items.size)
        assertEquals(OperationalAssignmentEventType.REJECTED, page.items.single().eventType)
        assertEquals("vehicle issue", page.items.single().reason)
    }

    @Test
    fun `empty local scope does not query event repository`() {
        val repository = mock(OperationalAssignmentEventLogRepository::class.java)
        val service = OperationalAssignmentEventQueryService(repository)
        val page = service.search(
            AssignmentAccessScope.assignedHubs(UUID.randomUUID(), emptySet()),
            null,
            null,
            null,
            1,
            100,
        )
        assertEquals(0, page.total)
        assertEquals(emptyList<Any>(), page.items)
    }
}
