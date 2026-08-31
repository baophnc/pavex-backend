package com.pavex.operations.repository

import com.pavex.operations.entity.OperationalAssignmentEventLog
import com.pavex.operations.entity.OperationalAssignmentEventType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OperationalAssignmentEventLogRepository : JpaRepository<OperationalAssignmentEventLog, UUID> {
    fun findAllByAssignmentIdAndEventTypeAndWorkforceMemberIdIsNotNull(
        assignmentId: UUID,
        eventType: OperationalAssignmentEventType,
    ): List<OperationalAssignmentEventLog>
}
