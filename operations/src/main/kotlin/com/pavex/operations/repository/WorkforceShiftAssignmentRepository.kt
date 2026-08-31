package com.pavex.operations.repository

import com.pavex.operations.entity.WorkforceShiftAssignment
import com.pavex.operations.entity.WorkforceShiftAssignmentStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkforceShiftAssignmentRepository : JpaRepository<WorkforceShiftAssignment, UUID> {
    fun findAllByShiftId(shiftId: UUID): List<WorkforceShiftAssignment>
    fun findAllByShiftIdIn(shiftIds: Collection<UUID>): List<WorkforceShiftAssignment>
    fun findByShiftIdAndWorkforceMemberId(shiftId: UUID, workforceMemberId: UUID): WorkforceShiftAssignment?
    fun findAllByWorkforceMemberId(workforceMemberId: UUID): List<WorkforceShiftAssignment>
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
