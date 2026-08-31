package com.pavex.operations.repository

import com.pavex.operations.entity.WorkforceMember
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkforceMemberRepository : JpaRepository<WorkforceMember, UUID> {
    fun findByUserId(userId: UUID): WorkforceMember?
    fun existsByUserId(userId: UUID): Boolean
    fun findAllByOrderByEmployeeCodeAsc(): List<WorkforceMember>
    fun findAllByStatusAndKind(status: WorkforceMemberStatus, kind: WorkforceMemberKind): List<WorkforceMember>
}
