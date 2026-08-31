package com.pavex.operations.repository

import com.pavex.operations.entity.OperationalAssignmentEventLog
import com.pavex.operations.entity.OperationalAssignmentEventType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OperationalAssignmentEventLogRepository : JpaRepository<OperationalAssignmentEventLog, UUID> {
    fun findAllByAssignmentIdAndEventTypeAndWorkforceMemberIdIsNotNull(
        assignmentId: UUID,
        eventType: OperationalAssignmentEventType,
    ): List<OperationalAssignmentEventLog>

    @Query(
        value = """
        select e
        from OperationalAssignmentEventLog e, OperationalAssignment a
        where a.id = e.assignmentId
          and (:assignmentId is null or e.assignmentId = :assignmentId)
          and (:shipmentId is null or e.shipmentId = :shipmentId)
          and (:eventType is null or e.eventType = :eventType)
          and (:globalScope = true or a.hubId in :hubIds)
        """,
        countQuery = """
        select count(e)
        from OperationalAssignmentEventLog e, OperationalAssignment a
        where a.id = e.assignmentId
          and (:assignmentId is null or e.assignmentId = :assignmentId)
          and (:shipmentId is null or e.shipmentId = :shipmentId)
          and (:eventType is null or e.eventType = :eventType)
          and (:globalScope = true or a.hubId in :hubIds)
        """,
    )
    fun search(
        @Param("assignmentId") assignmentId: UUID?,
        @Param("shipmentId") shipmentId: UUID?,
        @Param("eventType") eventType: OperationalAssignmentEventType?,
        @Param("globalScope") globalScope: Boolean,
        @Param("hubIds") hubIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<OperationalAssignmentEventLog>
}
