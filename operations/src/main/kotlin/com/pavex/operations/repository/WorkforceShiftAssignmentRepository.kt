package com.pavex.operations.repository

import com.pavex.operations.entity.WorkforceShiftAssignment
import com.pavex.operations.entity.WorkforceShiftAssignmentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface WorkforceShiftAssignmentRepository : JpaRepository<WorkforceShiftAssignment, UUID> {
    fun findAllByShiftId(shiftId: UUID): List<WorkforceShiftAssignment>
    fun findAllByShiftIdIn(shiftIds: Collection<UUID>): List<WorkforceShiftAssignment>
    fun findByShiftIdAndWorkforceMemberId(shiftId: UUID, workforceMemberId: UUID): WorkforceShiftAssignment?
    fun findAllByWorkforceMemberId(workforceMemberId: UUID): List<WorkforceShiftAssignment>
    @Query(
        """
        select a
        from WorkforceShiftAssignment a
        where a.workforceMemberId = :workforceMemberId
          and exists (
                select s.id
                from WorkShift s
                where s.id = a.shiftId
                  and s.endsAt >= :fromTime
                  and s.startsAt <= :toTime
          )
        """,
    )
    fun findAllByWorkforceMemberIdAndShiftWindow(
        @Param("workforceMemberId") workforceMemberId: UUID,
        @Param("fromTime") from: Instant,
        @Param("toTime") to: Instant,
    ): List<WorkforceShiftAssignment>
    fun findAllByWorkforceMemberIdAndStatusIn(
        workforceMemberId: UUID,
        statuses: Collection<WorkforceShiftAssignmentStatus>,
    ): List<WorkforceShiftAssignment>
    fun findAllByWorkforceMemberIdInAndStatusIn(
        workforceMemberIds: Collection<UUID>,
        statuses: Collection<WorkforceShiftAssignmentStatus>,
    ): List<WorkforceShiftAssignment>
    fun findAllByShiftIdAndStatusIn(
        shiftId: UUID,
        statuses: Collection<WorkforceShiftAssignmentStatus>,
    ): List<WorkforceShiftAssignment>
}
