package com.pavex.operations.repository

import com.pavex.operations.entity.WorkforceMember
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WorkforceMemberRepository : JpaRepository<WorkforceMember, UUID> {
    fun findByUserId(userId: UUID): WorkforceMember?
    fun existsByUserId(userId: UUID): Boolean
    fun findAllByStatusAndKind(status: WorkforceMemberStatus, kind: WorkforceMemberKind): List<WorkforceMember>

    @Query(
        """
        select m.userId
        from WorkforceMember m
        where (:status is null or m.status = :status)
          and (:kind is null or m.kind = :kind)
          and (
                :globalAccess = true
                or exists (
                    select hm.id
                    from HubMembership hm
                    where hm.workforceMemberId = m.id
                      and hm.isActive = true
                      and hm.hubId in :allowedHubIds
                )
          )
          and (
                :hubId is null
                or exists (
                    select hm2.id
                    from HubMembership hm2
                    where hm2.workforceMemberId = m.id
                      and hm2.isActive = true
                      and hm2.hubId = :hubId
                )
          )
        """,
    )
    fun findVisibleUserIds(
        @Param("status") status: WorkforceMemberStatus?,
        @Param("kind") kind: WorkforceMemberKind?,
        @Param("globalAccess") globalAccess: Boolean,
        @Param("allowedHubIds") allowedHubIds: Collection<UUID>,
        @Param("hubId") hubId: UUID?,
    ): List<UUID>

    @Query(
        value = """
        select distinct m
        from WorkforceMember m
        where (:status is null or m.status = :status)
          and (:kind is null or m.kind = :kind)
          and (
                :globalAccess = true
                or exists (
                    select hm.id
                    from HubMembership hm
                    where hm.workforceMemberId = m.id
                      and hm.isActive = true
                      and hm.hubId in :allowedHubIds
                )
          )
          and (
                :hubId is null
                or exists (
                    select hm2.id
                    from HubMembership hm2
                    where hm2.workforceMemberId = m.id
                      and hm2.isActive = true
                      and hm2.hubId = :hubId
                )
          )
          and (
                :needle is null
                or lower(m.employeeCode) like lower(concat('%', :needle, '%'))
                or m.userId in :identityUserIds
          )
        """,
        countQuery = """
        select count(distinct m.id)
        from WorkforceMember m
        where (:status is null or m.status = :status)
          and (:kind is null or m.kind = :kind)
          and (
                :globalAccess = true
                or exists (
                    select hm.id
                    from HubMembership hm
                    where hm.workforceMemberId = m.id
                      and hm.isActive = true
                      and hm.hubId in :allowedHubIds
                )
          )
          and (
                :hubId is null
                or exists (
                    select hm2.id
                    from HubMembership hm2
                    where hm2.workforceMemberId = m.id
                      and hm2.isActive = true
                      and hm2.hubId = :hubId
                )
          )
          and (
                :needle is null
                or lower(m.employeeCode) like lower(concat('%', :needle, '%'))
                or m.userId in :identityUserIds
          )
        """,
    )
    fun searchVisible(
        @Param("status") status: WorkforceMemberStatus?,
        @Param("kind") kind: WorkforceMemberKind?,
        @Param("globalAccess") globalAccess: Boolean,
        @Param("allowedHubIds") allowedHubIds: Collection<UUID>,
        @Param("hubId") hubId: UUID?,
        @Param("needle") needle: String?,
        @Param("identityUserIds") identityUserIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<WorkforceMember>
}
