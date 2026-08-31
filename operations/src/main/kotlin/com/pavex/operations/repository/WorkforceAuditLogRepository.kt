package com.pavex.operations.repository

import com.pavex.operations.entity.WorkforceAuditLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkforceAuditLogRepository : JpaRepository<WorkforceAuditLog, UUID> {
    fun findAllByWorkforceMemberIdOrderByOccurredAtDesc(workforceMemberId: UUID): List<WorkforceAuditLog>
}
