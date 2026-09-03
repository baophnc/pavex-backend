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

    @Query(
        """
        select a
        from OperationalAssignment a
        where (:globalAccess = true or a.hubId in :allowedHubIds)
          and (:shipmentId is null or a.shipmentId = :shipmentId)
          and (:hubId is null or a.hubId = :hubId)
          and (:status is null or a.status = :status)
        """,
    )
    fun searchVisible(
        @Param("globalAccess") globalAccess: Boolean,
        @Param("allowedHubIds") allowedHubIds: Collection<UUID>,
        @Param("shipmentId") shipmentId: UUID?,
        @Param("hubId") hubId: UUID?,
        @Param("status") status: OperationalAssignmentStatus?,
        pageable: Pageable,
    ): List<OperationalAssignment>

    @Query("select a.id from OperationalAssignment a where a.status in :statuses order by a.priority desc, a.createdAt asc, a.id asc")
    fun findDispatchableIds(
        @Param("statuses") statuses: Collection<OperationalAssignmentStatus>,
        pageable: Pageable,
    ): List<UUID>
}
