package com.pavex.pricing.repository

import com.pavex.pricing.entity.RatePlan
import com.pavex.pricing.entity.RatePlanStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface RatePlanRepository : JpaRepository<RatePlan, UUID> {
    fun existsByCodeIgnoreCaseAndRevision(code: String, revision: Int): Boolean

    @EntityGraph(attributePaths = ["rules"])
    fun findAllByStatusOrderByUpdatedAtDesc(status: RatePlanStatus): List<RatePlan>

    @EntityGraph(attributePaths = ["rules"])
    fun findAllByOrderByUpdatedAtDesc(): List<RatePlan>

    @EntityGraph(attributePaths = ["rules"])
    @Query("select distinct p from RatePlan p where p.id = :id")
    fun findWithRulesById(@Param("id") id: UUID): RatePlan?

    @EntityGraph(attributePaths = ["rules"])
    @Query(
        """
        select distinct p from RatePlan p
        where p.status = :status
          and p.effectiveFrom <= :at
          and (p.effectiveTo is null or p.effectiveTo > :at)
        order by p.effectiveFrom desc, p.revision desc
        """,
    )
    fun findActiveAt(@Param("status") status: RatePlanStatus, @Param("at") at: Instant): List<RatePlan>
}
