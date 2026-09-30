package com.pavex.operations.repository

import com.pavex.operations.entity.OperationalAssignment
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.entity.OperationalAssignmentType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
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

    /** Waiting jobs, least recently tried first, so a large backlog cannot starve jobs behind it. */
    @Query(
        value = """
        select a.id
        from operations.operational_assignments a
        where a.status in ('PENDING', 'READY', 'REJECTED')
        order by a.last_dispatch_attempt_at asc nulls first, a.priority desc, a.created_at asc, a.id asc
        limit :limit
        """,
        nativeQuery = true,
    )
    fun findDispatchableIds(@Param("limit") limit: Int): List<UUID>

    /** Sends a job that found nobody to the back of the queue; bypasses the version so it never races a real change. */
    @Modifying
    @Query(
        value = "update operations.operational_assignments set last_dispatch_attempt_at = :now where id = :id",
        nativeQuery = true,
    )
    fun markDispatchAttempt(@Param("id") id: UUID, @Param("now") now: Instant): Int

    /** Moves waiting jobs of these hubs to the front of the queue because someone there became free. */
    @Modifying
    @Query(
        value = """
        update operations.operational_assignments
        set last_dispatch_attempt_at = null
        where hub_id in (:hubIds)
          and status in ('PENDING', 'READY', 'REJECTED')
          and last_dispatch_attempt_at is not null
        """,
        nativeQuery = true,
    )
    fun wakeDispatchable(@Param("hubIds") hubIds: Collection<UUID>): Int

    fun findAllByStatusAndAssignedByUserIdIsNullAndAssignedAtBefore(
        status: OperationalAssignmentStatus,
        cutoff: Instant,
        pageable: Pageable,
    ): List<OperationalAssignment>
}
