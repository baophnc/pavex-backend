package com.pavex.operations.controller

import com.pavex.contracts.operations.HubPersonSnapshot
import com.pavex.contracts.operations.WorkPersonSnapshot
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Who works where, for Notifications: the hubs and kind of a sender, and the active people of a
 * hub to address a message or an alert to. Only user ids and kinds leave Operations.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1/operations/notifications")
class InternalNotificationsOperationsController(
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
) {
    @GetMapping("/people/{userId}")
    @Transactional(readOnly = true)
    fun person(@PathVariable userId: UUID): WorkPersonSnapshot {
        val member = members.findByUserId(userId)?.takeIf { it.status == WorkforceMemberStatus.ACTIVE }
            ?: return WorkPersonSnapshot(userId, null, emptyList())
        val hubIds = memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id).map { it.hubId }
        return WorkPersonSnapshot(userId, member.kind.name, hubIds)
    }

    @GetMapping("/hubs/{hubId}/people")
    @Transactional(readOnly = true)
    fun hubPeople(@PathVariable hubId: UUID, @RequestParam(required = false) kinds: List<String>?): List<HubPersonSnapshot> {
        val wanted = kinds.orEmpty().mapNotNull { runCatching { WorkforceMemberKind.valueOf(it) }.getOrNull() }.toSet()
        val memberIds = memberships.findAllByHubIdAndIsActiveTrue(hubId).map { it.workforceMemberId }
        return members.findAllById(memberIds)
            .filter { it.status == WorkforceMemberStatus.ACTIVE && (wanted.isEmpty() || it.kind in wanted) }
            .map { HubPersonSnapshot(it.userId, it.kind.name) }
    }
}
