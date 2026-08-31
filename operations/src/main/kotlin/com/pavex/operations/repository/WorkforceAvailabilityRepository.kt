package com.pavex.operations.repository

import com.pavex.operations.entity.WorkforceAvailability
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkforceAvailabilityRepository : JpaRepository<WorkforceAvailability, UUID> {
    fun findAllByWorkforceMemberIdIn(workforceMemberIds: Collection<UUID>): List<WorkforceAvailability>
}
