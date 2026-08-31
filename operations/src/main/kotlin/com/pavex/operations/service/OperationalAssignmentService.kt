package com.pavex.operations.service

import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.dto.MyAvailabilityResponse
import com.pavex.operations.dto.OperationalAssignmentResponse
import com.pavex.operations.dto.ShipmentAssignmentPlan
import com.pavex.operations.entity.OperationalAssignment
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.entity.OperationalAssignmentType
import com.pavex.operations.entity.WorkShiftStatus
import com.pavex.operations.entity.WorkforceAvailabilityStatus
import com.pavex.operations.entity.WorkforceMember
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.entity.WorkforceShiftAssignmentStatus
import com.pavex.operations.exception.InvalidOperationsRequestException
import com.pavex.operations.exception.OperationsConflictException
import com.pavex.operations.exception.OperationsNotFoundException
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.OperationalAssignmentRepository
import com.pavex.operations.repository.WorkShiftRepository
import com.pavex.operations.repository.WorkforceAvailabilityRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import com.pavex.operations.repository.WorkforceShiftAssignmentRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OperationalAssignmentService(
    private val assignments: OperationalAssignmentRepository,
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
    private val availabilities: WorkforceAvailabilityRepository,
    private val shiftAssignments: WorkforceShiftAssignmentRepository,
    private val shifts: WorkShiftRepository,
    private val identity: IdentityWorkforceClient,
    private val locks: AssignmentLockService,
) {
    @Transactional
    fun plan(plan: ShipmentAssignmentPlan): List<OperationalAssignmentResponse> {
        validatePlan(plan)
        locks.shipment(plan.shipmentId.toString().replace("-", ""))

        val existing = assignments.findAllByShipmentIdOrderBySequenceAsc(plan.shipmentId)
        if (existing.isNotEmpty()) return existing.map(OperationalAssignmentResponse::from)

        val drafts = buildDrafts(plan)
        drafts.map { "${it.hubId}:${it.requiredKind}" }.distinct().sorted().forEach(locks::pool)
        val plannedLoads = mutableMapOf<UUID, Int>()
        val now = Instant.now()
        val created = drafts.map { draft ->
            val assignment = OperationalAssignment.create(
                shipmentId = plan.shipmentId,
                sequence = draft.sequence,
                routeLegId = draft.routeLegId,
                type = draft.type,
                requiredKind = draft.requiredKind,
                hubId = draft.hubId,
                priority = plan.priority,
                now = now,
            )
            selectCandidate(draft.hubId, draft.requiredKind, plannedLoads, now)?.let { memberId ->
                assignment.assign(memberId, null, now)
                plannedLoads[memberId] = plannedLoads.getOrDefault(memberId, 0) + 1
            }
            assignment
        }

        return assignments.saveAllAndFlush(created)
            .sortedBy { it.sequence }
            .map(OperationalAssignmentResponse::from)
    }

    @Transactional(readOnly = true)
    fun search(
        scope: AssignmentAccessScope,
        shipmentId: UUID?,
        hubId: UUID?,
        status: OperationalAssignmentStatus?,
    ): List<OperationalAssignmentResponse> {
        if (hubId != null && !scope.canAccessHub(hubId)) throw AccessDeniedException("Hub is outside the allowed scope")
        return assignments.findAllByOrderByPriorityDescCreatedAtAsc()
            .asSequence()
            .filter { scope.isGlobal || it.hubId in scope.hubIds }
            .filter { shipmentId == null || it.shipmentId == shipmentId }
            .filter { hubId == null || it.hubId == hubId }
            .filter { status == null || it.status == status }
            .take(MAX_ADMIN_RESULTS)
            .map(OperationalAssignmentResponse::from)
            .toList()
    }

    @Transactional(readOnly = true)
    fun mine(userId: UUID): List<OperationalAssignmentResponse> {
        val member = activeMember(userId) ?: return emptyList()
        return assignments.findAllByAssigneeWorkforceMemberIdAndStatusInOrderByPriorityDescCreatedAtAsc(
            member.id,
            ACTIVE_STATUSES,
        ).map(OperationalAssignmentResponse::from)
    }

    @Transactional
    fun setMyAvailability(userId: UUID, status: WorkforceAvailabilityStatus): MyAvailabilityResponse {
        val member = activeMember(userId)
            ?: throw OperationsNotFoundException("workforce_member_not_found", "An active workforce member was not found.")
        val availability = availabilities.findById(member.id).orElseThrow {
            OperationsConflictException("workforce_availability_not_found", "Workforce availability is not configured for this member.")
        }
        availability.setStatus(status, Instant.now())
        availabilities.saveAndFlush(availability)
        return availabilityResponse(member.id, availability.status, availability.maxConcurrentAssignments, availability.updatedAt)
    }

    @Transactional
    fun assign(
        actorUserId: UUID,
        scope: AssignmentAccessScope,
        assignmentId: UUID,
        memberId: UUID,
    ): OperationalAssignmentResponse {
        val assignment = findAssignment(assignmentId)
        ensureScope(scope, assignment.hubId)
        locks.pool("${assignment.hubId}:${assignment.requiredKind}")
        if (!isEligible(memberId, assignment, Instant.now())) {
            throw InvalidOperationsRequestException(
                "assignee_not_eligible",
                "The member is unavailable, at capacity, not checked in, or lacks the required hub/workforce/Identity access.",
            )
        }
        return mutate(assignment) { assignment.assign(memberId, actorUserId, Instant.now()) }
    }

    @Transactional
    fun cancel(
        scope: AssignmentAccessScope,
        assignmentId: UUID,
        reason: String,
    ): OperationalAssignmentResponse {
        val assignment = findAssignment(assignmentId)
        ensureScope(scope, assignment.hubId)
        return mutate(assignment) { assignment.cancel(reason, Instant.now()) }
    }

    @Transactional
    fun cancelShipment(shipmentId: UUID, reason: String) {
        require(shipmentId != ZERO_UUID) { "Shipment is required." }
        val now = Instant.now()
        assignments.findAllByShipmentIdOrderBySequenceAsc(shipmentId)
            .filter { it.status !in TERMINAL_STATUSES }
            .forEach { assignment -> assignment.cancel(reason, now) }
    }

    @Transactional
    fun accept(userId: UUID, assignmentId: UUID): OperationalAssignmentResponse =
        mutateOwned(userId, assignmentId) { assignment, member -> assignment.accept(member.id, Instant.now()) }

    @Transactional
    fun reject(userId: UUID, assignmentId: UUID, reason: String): OperationalAssignmentResponse =
        mutateOwned(userId, assignmentId) { assignment, member -> assignment.reject(member.id, reason, Instant.now()) }

    @Transactional
    fun start(userId: UUID, assignmentId: UUID): OperationalAssignmentResponse =
        mutateOwned(userId, assignmentId) { assignment, member -> assignment.start(member.id, Instant.now()) }

    @Transactional
    fun complete(userId: UUID, assignmentId: UUID, note: String?): OperationalAssignmentResponse =
        mutateOwned(userId, assignmentId) { assignment, member -> assignment.complete(member.id, note, Instant.now()) }

    private fun mutateOwned(
        userId: UUID,
        assignmentId: UUID,
        mutation: (OperationalAssignment, WorkforceMember) -> Boolean,
    ): OperationalAssignmentResponse {
        val member = activeMember(userId)
            ?: throw OperationsNotFoundException("workforce_member_not_found", "An active workforce member was not found.")
        val assignment = findAssignment(assignmentId)
        if (assignment.assigneeWorkforceMemberId != member.id) {
            throw OperationsNotFoundException("assignment_not_found", "Assignment was not found.")
        }
        ensureExecutionAccess(member, assignment)
        return mutate(assignment) { mutation(assignment, member) }
    }

    private fun mutate(assignment: OperationalAssignment, mutation: () -> Boolean): OperationalAssignmentResponse = try {
        mutation()
        assignments.saveAndFlush(assignment)
        OperationalAssignmentResponse.from(assignment)
    } catch (exception: IllegalArgumentException) {
        throw InvalidOperationsRequestException("invalid_assignment_operation", exception.message ?: "Assignment operation is invalid.")
    } catch (exception: IllegalStateException) {
        throw OperationsConflictException("invalid_assignment_transition", exception.message ?: "Assignment transition is invalid.")
    } catch (_: ObjectOptimisticLockingFailureException) {
        throw OperationsConflictException("assignment_changed", "Assignment changed concurrently.")
    }

    private fun selectCandidate(
        hubId: UUID,
        kind: WorkforceMemberKind,
        plannedLoads: Map<UUID, Int>,
        now: Instant,
    ): UUID? {
        val candidates = eligibleBaseCandidates(hubId, kind, now)
        if (candidates.isEmpty()) return null
        val candidateIds = candidates.map { it.member.id }
        val activeLoads = assignments.findAllByAssigneeWorkforceMemberIdInAndStatusIn(candidateIds, ACTIVE_STATUSES)
            .groupingBy { requireNotNull(it.assigneeWorkforceMemberId) }
            .eachCount()
        return candidates
            .map { candidate ->
                val load = activeLoads.getOrDefault(candidate.member.id, 0) + plannedLoads.getOrDefault(candidate.member.id, 0)
                CandidateLoad(candidate.member.id, load, candidate.maximum)
            }
            .filter { it.load < it.maximum }
            .sortedWith(compareBy<CandidateLoad>({ it.load.toDouble() / it.maximum }, { it.load }, { it.memberId }))
            .firstOrNull()
            ?.memberId
    }

    private fun isEligible(memberId: UUID, assignment: OperationalAssignment, now: Instant): Boolean {
        val candidate = eligibleBaseCandidates(assignment.hubId, assignment.requiredKind, now)
            .firstOrNull { it.member.id == memberId }
            ?: return false
        val load = assignments.findAllByAssigneeWorkforceMemberIdAndStatusIn(memberId, ACTIVE_STATUSES)
            .count { it.id != assignment.id }
        return load < candidate.maximum
    }

    private fun eligibleBaseCandidates(hubId: UUID, kind: WorkforceMemberKind, now: Instant): List<Candidate> {
        val memberCandidates = members.findAllByStatusAndKind(WorkforceMemberStatus.ACTIVE, kind)
        if (memberCandidates.isEmpty()) return emptyList()
        val ids = memberCandidates.map { it.id }
        val activeHubMembers = memberships.findAllByWorkforceMemberIdIn(ids)
            .filter { it.isActive && it.hubId == hubId }
            .map { it.workforceMemberId }
            .toSet()
        val hubCandidates = memberCandidates.filter { it.id in activeHubMembers }
        if (hubCandidates.isEmpty()) return emptyList()
        val hubCandidateIds = hubCandidates.map { it.id }
        val availabilityByMember = availabilities.findAllByWorkforceMemberIdIn(hubCandidateIds).associateBy { it.workforceMemberId }

        val checkedInRoster = shiftAssignments.findAllByWorkforceMemberIdInAndStatusIn(
            hubCandidateIds,
            listOf(WorkforceShiftAssignmentStatus.CHECKED_IN),
        )
        val shiftById = shifts.findAllByIdIn(checkedInRoster.map { it.shiftId }.distinct()).associateBy { it.id }
        val checkedInAtHub = checkedInRoster.filter { roster ->
            val shift = shiftById[roster.shiftId]
            shift != null &&
                shift.status == WorkShiftStatus.SCHEDULED &&
                shift.hubId == hubId &&
                !now.isBefore(shift.startsAt) &&
                now < shift.endsAt
        }.map { it.workforceMemberId }.toSet()

        val locallyEligible = hubCandidates.filter { member ->
            val availability = availabilityByMember[member.id]
            member.id in checkedInAtHub && availability?.status == WorkforceAvailabilityStatus.AVAILABLE
        }
        if (locallyEligible.isEmpty()) return emptyList()
        val identityByUser = identity.snapshots(locallyEligible.map { it.userId }).associateBy { it.userId }
        return locallyEligible.mapNotNull { member ->
            val availability = availabilityByMember[member.id] ?: return@mapNotNull null
            val identitySnapshot = identityByUser[member.userId] ?: return@mapNotNull null
            if (identitySnapshot.accountStatus != "ACTIVE" || !hasCompatibleRole(kind, identitySnapshot.roles.toSet())) return@mapNotNull null
            Candidate(member, availability.maxConcurrentAssignments)
        }
    }

    private fun ensureExecutionAccess(member: WorkforceMember, assignment: OperationalAssignment) {
        if (member.kind != assignment.requiredKind) {
            throw InvalidOperationsRequestException("assignee_not_eligible", "The workforce kind no longer matches this assignment.")
        }
        val atHub = memberships.findByWorkforceMemberIdAndHubId(member.id, assignment.hubId)?.isActive == true
        if (!atHub) {
            throw InvalidOperationsRequestException("assignee_not_eligible", "The member is no longer assigned to the required hub.")
        }
        val snapshot = identity.snapshots(listOf(member.userId)).singleOrNull()
        if (snapshot == null || snapshot.accountStatus != "ACTIVE" || !hasCompatibleRole(member.kind, snapshot.roles.toSet())) {
            throw InvalidOperationsRequestException(
                "identity_access_revoked",
                "The Identity account is inactive or no longer has the role required for this assignment.",
            )
        }
    }

    private fun hasCompatibleRole(kind: WorkforceMemberKind, roles: Set<String>): Boolean = when (kind) {
        WorkforceMemberKind.WAREHOUSE_OPERATOR -> "WAREHOUSE_STAFF" in roles
        WorkforceMemberKind.COURIER, WorkforceMemberKind.LINE_HAUL_DRIVER -> "DRIVER" in roles
        WorkforceMemberKind.DISPATCHER -> "OPERATIONS_MANAGER" in roles || "HUB_MANAGER" in roles
    }

    private fun activeMember(userId: UUID): WorkforceMember? = members.findByUserId(userId)
        ?.takeIf { it.status == WorkforceMemberStatus.ACTIVE }

    private fun findAssignment(assignmentId: UUID): OperationalAssignment = assignments.findById(assignmentId).orElseThrow {
        OperationsNotFoundException("assignment_not_found", "Assignment was not found.")
    }

    private fun ensureScope(scope: AssignmentAccessScope, hubId: UUID) {
        if (!scope.canAccessHub(hubId)) throw OperationsNotFoundException("assignment_not_found", "Assignment was not found.")
    }

    private fun availabilityResponse(
        memberId: UUID,
        status: WorkforceAvailabilityStatus,
        maximum: Int,
        updatedAt: Instant,
    ): MyAvailabilityResponse {
        val active = assignments.findAllByAssigneeWorkforceMemberIdAndStatusIn(memberId, ACTIVE_STATUSES).size
        return MyAvailabilityResponse(
            workforceMemberId = memberId,
            status = status,
            maxConcurrentAssignments = maximum,
            activeAssignments = active,
            remainingCapacity = (maximum - active).coerceAtLeast(0),
            updatedAt = updatedAt,
        )
    }

    private fun validatePlan(plan: ShipmentAssignmentPlan) {
        if (
            plan.shipmentId == ZERO_UUID || plan.originHubId == ZERO_UUID || plan.destinationHubId == ZERO_UUID ||
            plan.priority !in 0..100 || plan.routeLegs.any {
                it.routeLegId == ZERO_UUID || it.sequence <= 0 || it.fromHubId == ZERO_UUID || it.toHubId == ZERO_UUID
            }
        ) {
            throw InvalidOperationsRequestException("invalid_assignment_plan", "Shipment assignment plan is invalid.")
        }
        val sequences = plan.routeLegs.map { it.sequence }
        if (sequences.distinct().size != sequences.size) {
            throw InvalidOperationsRequestException("invalid_assignment_plan", "Route-leg sequences must be unique.")
        }
    }

    internal fun buildDrafts(plan: ShipmentAssignmentPlan): List<AssignmentDraft> {
        var sequence = 1
        val drafts = mutableListOf(
            AssignmentDraft(sequence++, null, OperationalAssignmentType.PICKUP, WorkforceMemberKind.COURIER, plan.originHubId),
            AssignmentDraft(sequence++, null, OperationalAssignmentType.ORIGIN_HUB_HANDLING, WorkforceMemberKind.WAREHOUSE_OPERATOR, plan.originHubId),
        )
        plan.routeLegs.sortedBy { it.sequence }.forEach { leg ->
            drafts += AssignmentDraft(
                sequence++,
                leg.routeLegId,
                OperationalAssignmentType.LINE_HAUL,
                WorkforceMemberKind.LINE_HAUL_DRIVER,
                leg.fromHubId,
            )
        }
        drafts += AssignmentDraft(
            sequence++,
            null,
            OperationalAssignmentType.DESTINATION_HUB_HANDLING,
            WorkforceMemberKind.WAREHOUSE_OPERATOR,
            plan.destinationHubId,
        )
        drafts += AssignmentDraft(
            sequence,
            null,
            OperationalAssignmentType.LAST_MILE_DELIVERY,
            WorkforceMemberKind.COURIER,
            plan.destinationHubId,
        )
        return drafts
    }

    internal data class AssignmentDraft(
        val sequence: Int,
        val routeLegId: UUID?,
        val type: OperationalAssignmentType,
        val requiredKind: WorkforceMemberKind,
        val hubId: UUID,
    )

    private data class Candidate(val member: WorkforceMember, val maximum: Int)
    private data class CandidateLoad(val memberId: UUID, val load: Int, val maximum: Int)

    companion object {
        private val ZERO_UUID = UUID(0, 0)
        val ACTIVE_STATUSES = listOf(
            OperationalAssignmentStatus.ASSIGNED,
            OperationalAssignmentStatus.ACCEPTED,
            OperationalAssignmentStatus.IN_PROGRESS,
        )
        val TERMINAL_STATUSES = setOf(OperationalAssignmentStatus.COMPLETED, OperationalAssignmentStatus.CANCELLED)
        private const val MAX_ADMIN_RESULTS = 500
    }
}
