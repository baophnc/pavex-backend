package com.pavex.operations.service

import com.pavex.operations.dto.OperationalAssignmentEventPage
import com.pavex.operations.dto.OperationalAssignmentEventResponse
import com.pavex.operations.entity.OperationalAssignmentEventLog
import com.pavex.operations.entity.OperationalAssignmentEventType
import com.pavex.operations.repository.OperationalAssignmentEventLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OperationalAssignmentEventQueryService(
    private val events: OperationalAssignmentEventLogRepository,
) {
    @Transactional(readOnly = true)
    fun assignmentEvents(
        scope: AssignmentAccessScope,
        assignmentId: UUID,
        page: Int,
        pageSize: Int,
    ): OperationalAssignmentEventPage = search(
        scope = scope,
        assignmentId = assignmentId,
        shipmentId = null,
        eventType = null,
        page = page,
        pageSize = pageSize,
    )

    @Transactional(readOnly = true)
    fun search(
        scope: AssignmentAccessScope,
        assignmentId: UUID?,
        shipmentId: UUID?,
        eventType: OperationalAssignmentEventType?,
        page: Int,
        pageSize: Int,
    ): OperationalAssignmentEventPage {
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        if (!scope.isGlobal && scope.hubIds.isEmpty()) {
            return OperationalAssignmentEventPage(emptyList(), normalizedPage, normalizedPageSize, 0, 0)
        }

        val pageable = PageRequest.of(
            normalizedPage - 1,
            normalizedPageSize,
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")),
        )
        val result = events.search(
            assignmentId = assignmentId,
            shipmentId = shipmentId,
            eventType = eventType,
            globalScope = scope.isGlobal,
            hubIds = if (scope.isGlobal) GLOBAL_SENTINEL else scope.hubIds,
            pageable = pageable,
        )
        return OperationalAssignmentEventPage(
            items = result.content.map(::response),
            page = normalizedPage,
            pageSize = normalizedPageSize,
            total = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    private fun response(event: OperationalAssignmentEventLog): OperationalAssignmentEventResponse =
        OperationalAssignmentEventResponse(
            eventId = event.id,
            assignmentId = event.assignmentId,
            shipmentId = event.shipmentId,
            eventType = event.eventType,
            previousStatus = event.previousStatus,
            newStatus = event.newStatus,
            workforceMemberId = event.workforceMemberId,
            actorUserId = event.actorUserId,
            reason = event.reason,
            occurredAt = event.occurredAt,
        )

    companion object {
        private const val MAX_PAGE_SIZE = 200
        private val GLOBAL_SENTINEL = listOf(UUID(0, 0))
    }
}
