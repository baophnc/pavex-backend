package com.pavex.operations.repository

import com.pavex.operations.entity.WorkShift
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkShiftRepository : JpaRepository<WorkShift, UUID> {
    fun findAllByOrderByStartsAtAscCodeAsc(): List<WorkShift>
    fun findAllByIdIn(ids: Collection<UUID>): List<WorkShift>
}
