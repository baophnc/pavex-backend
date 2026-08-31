package com.pavex.operations.repository

import com.pavex.operations.entity.HubMembership
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HubMembershipRepository : JpaRepository<HubMembership, UUID> {
    fun findAllByWorkforceMemberId(workforceMemberId: UUID): List<HubMembership>
    fun findAllByWorkforceMemberIdIn(workforceMemberIds: Collection<UUID>): List<HubMembership>
    fun findAllByWorkforceMemberIdAndIsActiveTrue(workforceMemberId: UUID): List<HubMembership>
    fun findByWorkforceMemberIdAndHubId(workforceMemberId: UUID, hubId: UUID): HubMembership?
}
