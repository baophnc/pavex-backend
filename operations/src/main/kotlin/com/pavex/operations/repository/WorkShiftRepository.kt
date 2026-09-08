package com.pavex.operations.repository

import com.pavex.operations.entity.WorkShift
import com.pavex.operations.entity.WorkShiftStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface WorkShiftRepository : JpaRepository<WorkShift, UUID> {
    fun findAllByIdIn(ids: Collection<UUID>): List<WorkShift>

    @Query(
        value = """
        select s
        from WorkShift s
        where (:globalAccess = true or s.hubId in :allowedHubIds)
          and (:hubId is null or s.hubId = :hubId)
          and (:status is null or s.status = :status)
          and s.endsAt >= coalesce(:fromTime, s.endsAt)
          and s.startsAt <= coalesce(:toTime, s.startsAt)
        """,
        countQuery = """
        select count(s.id)
        from WorkShift s
        where (:globalAccess = true or s.hubId in :allowedHubIds)
          and (:hubId is null or s.hubId = :hubId)
          and (:status is null or s.status = :status)
          and s.endsAt >= coalesce(:fromTime, s.endsAt)
          and s.startsAt <= coalesce(:toTime, s.startsAt)
        """,
    )
    fun searchVisible(
        @Param("globalAccess") globalAccess: Boolean,
        @Param("allowedHubIds") allowedHubIds: Collection<UUID>,
        @Param("hubId") hubId: UUID?,
        @Param("status") status: WorkShiftStatus?,
        @Param("fromTime") from: Instant?,
        @Param("toTime") to: Instant?,
        pageable: Pageable,
    ): Page<WorkShift>
}
