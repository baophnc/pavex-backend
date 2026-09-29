package com.pavex.operations.service

import com.pavex.common.security.PavexActor
import com.pavex.operations.client.NetworkHubClient
import com.pavex.operations.dto.MyHubResponse
import com.pavex.operations.dto.MyHubScopeResponse
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Tells a signed-in user which hubs their screens cover: head office sees every hub,
 * hub staff see the hubs of their active memberships (primary hub first).
 */
@Service
class MyHubScopeService(
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
    private val network: NetworkHubClient,
) {
    @Transactional(readOnly = true)
    fun resolve(actor: PavexActor): MyHubScopeResponse {
        val member = members.findByUserId(actor.userId)?.takeIf { it.status == WorkforceMemberStatus.ACTIVE }
        if (actor.seesAllHubs) {
            return MyHubScopeResponse(allHubs = true, workforceKind = member?.kind, hubs = emptyList())
        }
        val active = member?.let { memberships.findAllByWorkforceMemberIdAndIsActiveTrue(it.id) }.orEmpty()
        val primary = active.filter { it.isPrimary }.map { it.hubId }.toSet()
        val hubs = if (active.isEmpty()) emptyList() else network.snapshots(active.map { it.hubId })
        return MyHubScopeResponse(
            allHubs = false,
            workforceKind = member?.kind,
            hubs = hubs
                .map { MyHubResponse(it.hubId, it.code, it.name, it.type, it.status, it.provinceCode, it.wardCode, it.hubId in primary) }
                .sortedWith(compareByDescending<MyHubResponse> { it.primary }.thenBy { it.name }),
        )
    }
}
