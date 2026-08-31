package com.pavex.operations.repository

import com.pavex.operations.entity.OperationalAssignment
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.entity.OperationalAssignmentType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OperationalAssignmentRepository : JpaRepository<OperationalAssignment, UUID> {
    fun findAllByShipmentIdOrderBySequenceAsc(shipmentId: UUID): List<OperationalAssignment>
    fun findByShipmentIdAndRouteLegIdAndType(
        shipmentId: UUID,
        routeLegId: UUID,
        type: OperationalAssignmentType,
    ): OperationalAssignment?
    fun findByShipmentIdAndTypeAndHubId(
        shipmentId: UUID,
        type: OperationalAssignmentType,
        hubId: UUID,
    ): OperationalAssignment?
    fun findAllByOrderByPriorityDescCreatedAtAsc(): List<OperationalAssignment>
    fun findAllByAssigneeWorkforceMemberIdAndStatusInOrderByPriorityDescCreatedAtAsc(
        assigneeWorkforceMemberId: UUID,
        statuses: Collection<OperationalAssignmentStatus>,
    ): List<OperationalAssignment>
    fun findAllByAssigneeWorkforceMemberIdAndStatusIn(
        assigneeWorkforceMemberId: UUID,
        statuses: Collection<OperationalAssignmentStatus>,
    ): List<OperationalAssignment>
    fun findAllByAssigneeWorkforceMemberIdAndHubIdAndStatusIn(
        assigneeWorkforceMemberId: UUID,
        hubId: UUID,
        statuses: Collection<OperationalAssignmentStatus>,
    ): List<OperationalAssignment>
    fun findAllByAssigneeWorkforceMemberIdInAndStatusIn(
        assigneeWorkforceMemberIds: Collection<UUID>,
        statuses: Collection<OperationalAssignmentStatus>,
    ): List<OperationalAssignment>
    @Query("select a.id from OperationalAssignment a where a.status in :statuses order by a.priority desc, a.createdAt asc, a.id asc")
    fun findDispatchableIds(
        @Param("statuses") statuses: Collection<OperationalAssignmentStatus>,
        pageable: Pageable,
    ): List<UUID>

}
