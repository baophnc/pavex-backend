package com.pavex.operations.service

import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.OperationalAssignmentRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Keeps work moving when people come and go: jobs held by someone who leaves before accepting them
 * go back to the queue, and waiting jobs at a hub move to the front of the queue as soon as someone
 * there becomes free, so the redispatch worker offers them in its next cycle.
 */
@Service
class AssignmentDispatchQueue(
    private val assignments: OperationalAssignmentRepository,
    private val memberships: HubMembershipRepository,
    private val locks: AssignmentLockService,
    private val clock: Clock,
    @Value("\${operations.dispatch.accept-timeout:PT15M}")
    private val acceptTimeout: Duration = Duration.ofMinutes(15),
) {
    /**
     * Runs inside the transaction that takes these members off duty (check-out, absence, a cancelled
     * shift or roster entry). Accepted and started work stays with them.
     */
    fun releaseUnaccepted(memberIds: Collection<UUID>, now: Instant): Int {
        if (memberIds.isEmpty()) return 0
        val held = assignments.findAllByAssigneeWorkforceMemberIdInAndStatusIn(
            memberIds.distinct(),
            listOf(OperationalAssignmentStatus.ASSIGNED),
        )
        held.forEach { assignment ->
            assignment.lastActorUserId = null
            assignment.requeue(now)
        }
        if (held.isNotEmpty()) assignments.saveAllAndFlush(held)
        return held.size
    }

    /** Someone checked in or became available: their hubs' waiting jobs are tried first. */
    fun wake(memberId: UUID): Int {
        val hubIds = memberships.findAllByWorkforceMemberIdAndIsActiveTrue(memberId).map { it.hubId }.distinct()
        return if (hubIds.isEmpty()) 0 else assignments.wakeDispatchable(hubIds)
    }

    /**
     * Jobs handed out automatically that nobody accepted in time. A job a manager assigned by hand
     * stays with that person: the manager chose them and sees it waiting.
     */
    @Transactional(readOnly = true)
    fun unacceptedIds(limit: Int): List<UUID> = assignments.findAllByStatusAndAssignedByUserIdIsNullAndAssignedAtBefore(
        OperationalAssignmentStatus.ASSIGNED,
        Instant.now(clock).minus(acceptTimeout),
        PageRequest.of(0, limit, Sort.by("assignedAt", "id")),
    ).map { it.id }

    @Transactional
    fun releaseIfUnaccepted(assignmentId: UUID): Boolean {
        locks.assignment(assignmentId.toString().replace("-", ""))
        val assignment = assignments.findById(assignmentId).orElse(null) ?: return false
        val now = Instant.now(clock)
        val assignedAt = assignment.assignedAt
        val stale = assignment.status == OperationalAssignmentStatus.ASSIGNED &&
            assignment.assignedByUserId == null &&
            assignedAt != null && assignedAt < now.minus(acceptTimeout)
        if (!stale) return false
        assignment.lastActorUserId = null
        assignment.requeue(now)
        assignments.saveAndFlush(assignment)
        return true
    }
}
