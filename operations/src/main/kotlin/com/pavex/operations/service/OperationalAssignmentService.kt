package com.pavex.operations.service

import com.pavex.contracts.operations.ShipmentAssignmentAccessRequest
import com.pavex.contracts.operations.ShipmentAssignmentCompletionRequest
import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.dto.MyAvailabilityResponse
import com.pavex.operations.dto.OperationalAssignmentResponse
import com.pavex.operations.dto.ShipmentAssignmentPlan
import com.pavex.operations.entity.OperationalAssignment
import com.pavex.operations.entity.OperationalAssignmentEventType
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
import com.pavex.operations.repository.OperationalAssignmentEventLogRepository
import com.pavex.operations.repository.OperationalAssignmentRepository
import com.pavex.operations.repository.WorkShiftRepository
import com.pavex.operations.repository.WorkforceAvailabilityRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import com.pavex.operations.repository.WorkforceShiftAssignmentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class OperationalAssignmentService(
    private val assignments: OperationalAssignmentRepository,
    private val assignmentEvents: OperationalAssignmentEventLogRepository,
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
    private val availabilities: WorkforceAvailabilityRepository,
    private val shiftAssignments: WorkforceShiftAssignmentRepository,
    private val shifts: WorkShiftRepository,
    private val identity: IdentityWorkforceClient,
    private val locks: AssignmentLockService,
    private val queue: AssignmentDispatchQueue,
    private val clock: Clock,
    @Value("\${operations.dispatch.auto-assign-enabled:true}")
    private val autoAssignEnabled: Boolean = true,
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
        val now = Instant.now(clock)
        val created = drafts.mapIndexed { index, draft ->
            val initialStatus = if (index == 0) {
                OperationalAssignmentStatus.READY
            } else {
                OperationalAssignmentStatus.BLOCKED
            }
            val assignment = OperationalAssignment.create(
                shipmentId = plan.shipmentId,
                sequence = draft.sequence,
                routeLegId = draft.routeLegId,
                type = draft.type,
                requiredKind = draft.requiredKind,
                hubId = draft.hubId,
                priority = plan.priority,
                now = now,
                initialStatus = initialStatus,
            )
            if (initialStatus == OperationalAssignmentStatus.READY) {
                selectCandidate(draft.hubId, draft.requiredKind, plannedLoads, now)?.let { memberId ->
                    assignment.assign(memberId, null, now)
                    plannedLoads[memberId] = plannedLoads.getOrDefault(memberId, 0) + 1
                }
            }
            assignment
        }

        return assignments.saveAllAndFlush(created)
            .sortedBy { it.sequence }
            .map(OperationalAssignmentResponse::from)
    }

    @Transactional
    fun redispatch(assignmentId: UUID): Boolean {
        if (!autoAssignEnabled) return false
        locks.assignment(assignmentId.toString().replace("-", ""))
        val assignmentResult = assignments.findById(assignmentId)
        if (assignmentResult.isEmpty) return false
        val assignment = assignmentResult.get()
        if (assignment.status !in REDISPATCHABLE_STATUSES) return false

        locks.pool("${assignment.hubId}:${assignment.requiredKind}")
        val rejectedMembers = previousMembers(assignment.id, OperationalAssignmentEventType.REJECTED)
        // People it was taken back from (they left, or did not accept in time) are asked last.
        val releasedFrom = previousMembers(assignment.id, OperationalAssignmentEventType.REQUEUED)
        val now = Instant.now(clock)
        val memberId = selectCandidate(
            assignment.hubId,
            assignment.requiredKind,
            emptyMap(),
            now,
            rejectedMembers,
            releasedFrom,
        )
        if (memberId == null) {
            // Nobody free: go behind the jobs not tried yet, so a backlog cannot starve them.
            assignments.markDispatchAttempt(assignment.id, now)
            return false
        }

        return try {
            assignment.assign(memberId, null, now)
            assignments.saveAndFlush(assignment)
            true
        } catch (_: ObjectOptimisticLockingFailureException) {
            false
        }
    }

    private fun previousMembers(assignmentId: UUID, type: OperationalAssignmentEventType): Set<UUID> =
        assignmentEvents.findAllByAssignmentIdAndEventTypeAndWorkforceMemberIdIsNotNull(assignmentId, type)
            .mapNotNull { it.workforceMemberId }
            .toSet()

    @Transactional(readOnly = true)
    fun search(
        scope: AssignmentAccessScope,
        shipmentId: UUID?,
        hubId: UUID?,
        status: OperationalAssignmentStatus?,
    ): List<OperationalAssignmentResponse> {
        if (hubId != null && !scope.canAccessHub(hubId)) throw AccessDeniedException("Hub is outside the allowed scope")
        if (!scope.isGlobal && scope.hubIds.isEmpty()) return emptyList()
        val pageable = PageRequest.of(
            0,
            MAX_ADMIN_RESULTS,
            Sort.by(
                Sort.Order.desc("priority"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id"),
            ),
        )
        return assignments.searchVisible(
            globalAccess = scope.isGlobal,
            allowedHubIds = scope.hubIds.ifEmpty { setOf(QUERY_SENTINEL_UUID) },
            shipmentId = shipmentId,
            hubId = hubId,
            status = status,
            pageable = pageable,
        ).map(OperationalAssignmentResponse::from)
    }

    @Transactional(readOnly = true)
    fun mine(userId: UUID): List<OperationalAssignmentResponse> {
        val member = activeMember(userId) ?: return emptyList()
        return assignments.findAllByAssigneeWorkforceMemberIdAndStatusInOrderByPriorityDescCreatedAtAsc(
            member.id,
            ACTIVE_STATUSES,
        ).map(OperationalAssignmentResponse::from)
    }

    @Transactional(readOnly = true)
    fun myAvailability(userId: UUID): MyAvailabilityResponse {
        val member = activeMember(userId)
            ?: throw OperationsNotFoundException("workforce_member_not_found", "An active workforce member was not found.")
        val availability = availabilities.findById(member.id).orElseThrow {
            OperationsConflictException("workforce_availability_not_found", "Workforce availability is not configured for this member.")
        }
        return availabilityResponse(member.id, availability.status, availability.maxConcurrentAssignments, availability.updatedAt)
    }

    @Transactional
    fun setMyAvailability(userId: UUID, status: WorkforceAvailabilityStatus): MyAvailabilityResponse {
        val member = activeMember(userId)
            ?: throw OperationsNotFoundException("workforce_member_not_found", "An active workforce member was not found.")
        val availability = availabilities.findById(member.id).orElseThrow {
            OperationsConflictException("workforce_availability_not_found", "Workforce availability is not configured for this member.")
        }
        val now = Instant.now(clock)
        availability.setStatus(status, now)
        availabilities.saveAndFlush(availability)
        if (status == WorkforceAvailabilityStatus.AVAILABLE) {
            queue.wake(member.id)
        } else {
            queue.releaseUnaccepted(listOf(member.id), now)
        }
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
        if (!isEligible(memberId, assignment, Instant.now(clock))) {
            throw InvalidOperationsRequestException(
                "assignee_not_eligible",
                "The member is unavailable, at capacity, not checked in, or lacks the required hub/workforce/Identity access.",
            )
        }
        return mutate(assignment) { assignment.assign(memberId, actorUserId, Instant.now(clock)) }
    }

    @Transactional
    fun cancel(
        actorUserId: UUID,
        scope: AssignmentAccessScope,
        assignmentId: UUID,
        reason: String,
    ): OperationalAssignmentResponse {
        val assignment = findAssignment(assignmentId)
        ensureScope(scope, assignment.hubId)
        val wasBlocked = assignment.status == OperationalAssignmentStatus.BLOCKED
        assignment.lastActorUserId = actorUserId
        val now = Instant.now(clock)
        val response = mutate(assignment) { assignment.cancel(reason, now) }
        if (!wasBlocked) activateNext(assignment, now)
        return response
    }

    @Transactional
    fun cancelShipment(shipmentId: UUID, reason: String) {
        require(shipmentId != ZERO_UUID) { "Shipment is required." }
        val now = Instant.now(clock)
        assignments.findAllByShipmentIdOrderBySequenceAsc(shipmentId)
            .filter { it.status !in TERMINAL_STATUSES }
            .forEach { assignment ->
                assignment.lastActorUserId = null
                assignment.cancel(reason, now)
            }
    }

    @Transactional(readOnly = true)
    fun authorizeFromShipment(
        shipmentId: UUID,
        assignmentId: UUID,
        request: ShipmentAssignmentAccessRequest,
    ): OperationalAssignmentResponse {
        val member = activeMember(request.actorUserId)
            ?: throw OperationsNotFoundException("assignment_not_found", "An owned operational assignment was not found.")
        val assignment = findAssignment(assignmentId)
        validateShipmentAssignmentReference(shipmentId, assignment, request.expectedType.name, request.hubId, request.routeLegId)
        if (assignment.assigneeWorkforceMemberId != member.id) {
            throw OperationsNotFoundException("assignment_not_found", "An owned operational assignment was not found.")
        }
        ensureExecutionAccess(member, assignment)
        if (assignment.status !in setOf(OperationalAssignmentStatus.IN_PROGRESS, OperationalAssignmentStatus.COMPLETED)) {
            throw OperationsConflictException(
                "assignment_not_in_progress",
                "The operational assignment must be in progress before Shipment can execute this action.",
            )
        }
        return OperationalAssignmentResponse.from(assignment)
    }

    /**
     * Hub handling belongs to whoever at the hub scans the parcel in. A clerk or the hub manager who
     * scans takes the job over when nobody has accepted it yet, so scanning never waits on "Nhận việc".
     * Work someone already accepted or started stays theirs and the scan simply goes through.
     */
    @Transactional
    fun claimHubHandlingFromShipment(
        shipmentId: UUID,
        assignmentId: UUID,
        request: ShipmentAssignmentAccessRequest,
    ): OperationalAssignmentResponse {
        locks.assignment(assignmentId.toString().replace("-", ""))
        val assignment = findAssignment(assignmentId)
        validateShipmentAssignmentReference(shipmentId, assignment, request.expectedType.name, request.hubId, request.routeLegId)
        if (assignment.type !in HUB_HANDLING_TYPES) {
            throw OperationsConflictException("assignment_not_claimable", "Only hub handling is taken over by a scan.")
        }
        if (assignment.status in CLAIMED_STATUSES) return OperationalAssignmentResponse.from(assignment)
        if (assignment.status !in CLAIMABLE_STATUSES) {
            throw OperationsConflictException(
                "assignment_not_ready",
                "Hub handling for this shipment has not started or was cancelled.",
            )
        }
        val member = activeMember(request.actorUserId)
            ?: throw OperationsNotFoundException("assignment_not_found", "An active workforce member was not found.")
        ensureExecutionAccess(member, assignment)
        // One save per step, each with its own time, so the history reads taken over, accepted, started.
        mutate(assignment) { assignment.assign(member.id, request.actorUserId, Instant.now(clock)) }
        mutate(assignment) { assignment.accept(member.id, Instant.now(clock)) }
        return mutate(assignment) { assignment.start(member.id, Instant.now(clock)) }
    }

    @Transactional(readOnly = true)
    fun findRouteLegAssignment(shipmentId: UUID, routeLegId: UUID): OperationalAssignmentResponse? {
        if (shipmentId == ZERO_UUID || routeLegId == ZERO_UUID) return null
        return assignments.findByShipmentIdAndRouteLegIdAndType(
            shipmentId,
            routeLegId,
            OperationalAssignmentType.LINE_HAUL,
        )?.let(OperationalAssignmentResponse::from)
    }

    @Transactional(readOnly = true)
    fun findTypeAssignment(
        shipmentId: UUID,
        type: OperationalAssignmentType,
        hubId: UUID,
    ): OperationalAssignmentResponse? {
        if (shipmentId == ZERO_UUID || hubId == ZERO_UUID) return null
        return assignments.findByShipmentIdAndTypeAndHubId(shipmentId, type, hubId)
            ?.let(OperationalAssignmentResponse::from)
    }

    @Transactional
    fun completeFromShipment(
        shipmentId: UUID,
        assignmentId: UUID,
        request: ShipmentAssignmentCompletionRequest,
    ): OperationalAssignmentResponse {
        val assignment = findAssignment(assignmentId)
        validateShipmentAssignmentReference(shipmentId, assignment, request.expectedType.name, request.hubId, request.routeLegId)
        if (assignment.status == OperationalAssignmentStatus.COMPLETED) {
            return OperationalAssignmentResponse.from(assignment)
        }
        val assignee = assignment.assigneeWorkforceMemberId
            ?: throw OperationsConflictException("assignment_not_assigned", "The operational assignment does not have an assignee.")
        val now = Instant.now(clock)
        assignment.lastActorUserId = request.actorUserId
        val response = mutate(assignment) {
            if (assignment.status == OperationalAssignmentStatus.ACCEPTED) {
                assignment.start(assignee, now)
            }
            if (assignment.status != OperationalAssignmentStatus.IN_PROGRESS) {
                throw IllegalStateException("The assignment must be accepted or in progress before shipment-driven completion.")
            }
            assignment.complete(assignee, request.note, now)
        }
        activateNext(assignment, now)
        return response
    }

    @Transactional
    fun accept(userId: UUID, assignmentId: UUID): OperationalAssignmentResponse =
        mutateOwned(userId, assignmentId) { assignment, member -> assignment.accept(member.id, Instant.now(clock)) }

    @Transactional
    fun reject(userId: UUID, assignmentId: UUID, reason: String): OperationalAssignmentResponse =
        mutateOwned(userId, assignmentId) { assignment, member -> assignment.reject(member.id, reason, Instant.now(clock)) }

    @Transactional
    fun start(userId: UUID, assignmentId: UUID): OperationalAssignmentResponse =
        mutateOwned(userId, assignmentId) { assignment, member -> assignment.start(member.id, Instant.now(clock)) }

    @Transactional
    fun complete(userId: UUID, assignmentId: UUID, note: String?): OperationalAssignmentResponse {
        val member = activeMember(userId)
            ?: throw OperationsNotFoundException("workforce_member_not_found", "An active workforce member was not found.")
        val assignment = findAssignment(assignmentId)
        if (assignment.assigneeWorkforceMemberId != member.id) {
            throw OperationsNotFoundException("assignment_not_found", "Assignment was not found.")
        }
        throw OperationsConflictException(
            "shipment_event_required",
            "Shipment movement assignments are completed by a parcel scan or delivery event.",
        )
    }

    private fun validateShipmentAssignmentReference(
        shipmentId: UUID,
        assignment: OperationalAssignment,
        expectedTypeName: String,
        hubId: UUID,
        routeLegId: UUID?,
    ) {
        val expectedType = try {
            OperationalAssignmentType.valueOf(expectedTypeName)
        } catch (_: IllegalArgumentException) {
            throw InvalidOperationsRequestException("assignment_type_invalid", "Assignment type is invalid.")
        }
        val mismatch = assignment.shipmentId != shipmentId ||
            assignment.type != expectedType ||
            assignment.hubId != hubId ||
            (routeLegId != null && assignment.routeLegId != routeLegId) ||
            (expectedType == OperationalAssignmentType.LINE_HAUL && routeLegId == null)
        if (mismatch) {
            throw OperationsConflictException(
                "assignment_mismatch",
                "The operational assignment does not match this shipment, action, hub or route leg.",
            )
        }
    }

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
        assignment.lastActorUserId = userId
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

    private fun activateNext(completed: OperationalAssignment, now: Instant) {
        val next = assignments.findAllByShipmentIdOrderBySequenceAsc(completed.shipmentId)
            .firstOrNull { it.sequence > completed.sequence && it.status !in TERMINAL_STATUSES }
            ?: return
        if (next.status != OperationalAssignmentStatus.BLOCKED) return

        next.activate(now)
        assignments.saveAndFlush(next)
        selectCandidate(next.hubId, next.requiredKind, emptyMap(), now)?.let { memberId ->
            next.assign(memberId, null, now)
            assignments.saveAndFlush(next)
        }
    }

    private fun selectCandidate(
        hubId: UUID,
        kind: WorkforceMemberKind,
        plannedLoads: Map<UUID, Int>,
        now: Instant,
        excludedMemberIds: Set<UUID> = emptySet(),
        avoidedMemberIds: Set<UUID> = emptySet(),
    ): UUID? {
        // Applies to initial planning, next-leg activation and background redispatch.
        // Manual assign() still performs the full scope/shift/capacity checks.
        if (!autoAssignEnabled) return null
        val candidates = eligibleBaseCandidates(hubId, kind, now)
            .filterNot { it.member.id in excludedMemberIds }
        if (candidates.isEmpty()) return null
        val candidateIds = candidates.map { it.member.id }
        val activeLoads = assignments.findAllByAssigneeWorkforceMemberIdInAndStatusIn(candidateIds, ACTIVE_STATUSES)
            .groupingBy { requireNotNull(it.assigneeWorkforceMemberId) }
            .eachCount()
        return candidates
            .map { candidate ->
                val load = activeLoads.getOrDefault(candidate.member.id, 0) + plannedLoads.getOrDefault(candidate.member.id, 0)
                CandidateLoad(candidate.member.id, load, candidate.maximum, candidate.fallback, candidate.member.id in avoidedMemberIds)
            }
            .filter { it.load < it.maximum }
            .sortedWith(CANDIDATE_ORDER)
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
        val hubMemberIds = memberships.findAllByHubIdAndIsActiveTrue(hubId).map { it.workforceMemberId }.distinct()
        if (hubMemberIds.isEmpty()) return emptyList()
        val kinds = eligibleKinds(kind)
        val hubCandidates = members.findAllById(hubMemberIds)
            .filter { it.status == WorkforceMemberStatus.ACTIVE && it.kind in kinds }
        if (hubCandidates.isEmpty()) return emptyList()
        val hubCandidateIds = hubCandidates.map { it.id }
        val hubsByMember = memberships.findAllByWorkforceMemberIdIn(hubCandidateIds)
            .filter { it.isActive }
            .groupBy({ it.workforceMemberId }, { it.hubId })
        val availabilityByMember = availabilities.findAllByWorkforceMemberIdIn(hubCandidateIds).associateBy { it.workforceMemberId }
        val kindByMember = hubCandidates.associate { it.id to it.kind }

        val checkedInRoster = shiftAssignments.findAllByWorkforceMemberIdInAndStatusIn(
            hubCandidateIds,
            listOf(WorkforceShiftAssignmentStatus.CHECKED_IN),
        )
        val shiftById = shifts.findAllByIdIn(checkedInRoster.map { it.shiftId }.distinct()).associateBy { it.id }
        val checkedInAtHub = checkedInRoster.filter { roster ->
            val shift = shiftById[roster.shiftId]
            val memberKind = kindByMember[roster.workforceMemberId]
            shift != null && memberKind != null &&
                shift.status == WorkShiftStatus.SCHEDULED &&
                ShiftCoverage.covers(memberKind, hubId, shift.hubId, hubsByMember[roster.workforceMemberId].orEmpty()) &&
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
            if (identitySnapshot.accountStatus != "ACTIVE" || !WorkforceRolePolicy.isCompatible(member.kind, identitySnapshot.roles)) return@mapNotNull null
            Candidate(member, availability.maxConcurrentAssignments, fallback = member.kind != kind)
        }
    }

    private fun ensureExecutionAccess(member: WorkforceMember, assignment: OperationalAssignment) {
        if (member.kind !in eligibleKinds(assignment.requiredKind)) {
            throw InvalidOperationsRequestException("assignee_not_eligible", "The workforce kind no longer matches this assignment.")
        }
        val atHub = memberships.findByWorkforceMemberIdAndHubId(member.id, assignment.hubId)?.isActive == true
        if (!atHub) {
            throw InvalidOperationsRequestException("assignee_not_eligible", "The member is no longer assigned to the required hub.")
        }
        val snapshot = identity.snapshots(listOf(member.userId)).singleOrNull()
        if (snapshot == null || snapshot.accountStatus != "ACTIVE" || !WorkforceRolePolicy.isCompatible(member.kind, snapshot.roles)) {
            throw InvalidOperationsRequestException(
                "identity_access_revoked",
                "The Identity account is inactive or no longer has the role required for this assignment.",
            )
        }
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
            if (leg.toHubId != plan.destinationHubId) {
                drafts += AssignmentDraft(
                    sequence++,
                    leg.routeLegId,
                    OperationalAssignmentType.TRANSIT_HUB_HANDLING,
                    WorkforceMemberKind.WAREHOUSE_OPERATOR,
                    leg.toHubId,
                )
            }
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

    private data class Candidate(val member: WorkforceMember, val maximum: Int, val fallback: Boolean)
    internal data class CandidateLoad(
        val memberId: UUID,
        val load: Int,
        val maximum: Int,
        val fallback: Boolean = false,
        val avoided: Boolean = false,
    )

    companion object {
        private val ZERO_UUID = UUID(0, 0)
        private val QUERY_SENTINEL_UUID = UUID(0, 1)
        val ACTIVE_STATUSES = listOf(
            OperationalAssignmentStatus.ASSIGNED,
            OperationalAssignmentStatus.ACCEPTED,
            OperationalAssignmentStatus.IN_PROGRESS,
        )
        val TERMINAL_STATUSES = setOf(OperationalAssignmentStatus.COMPLETED, OperationalAssignmentStatus.CANCELLED)
        private val REDISPATCHABLE_STATUSES = setOf(
            OperationalAssignmentStatus.PENDING,
            OperationalAssignmentStatus.READY,
            OperationalAssignmentStatus.REJECTED,
        )
        private const val MAX_ADMIN_RESULTS = 500
        private val HUB_HANDLING_TYPES = setOf(
            OperationalAssignmentType.ORIGIN_HUB_HANDLING,
            OperationalAssignmentType.TRANSIT_HUB_HANDLING,
            OperationalAssignmentType.DESTINATION_HUB_HANDLING,
        )
        private val CLAIMED_STATUSES = setOf(
            OperationalAssignmentStatus.ACCEPTED,
            OperationalAssignmentStatus.IN_PROGRESS,
            OperationalAssignmentStatus.COMPLETED,
        )
        private val CLAIMABLE_STATUSES = setOf(
            OperationalAssignmentStatus.PENDING,
            OperationalAssignmentStatus.READY,
            OperationalAssignmentStatus.ASSIGNED,
            OperationalAssignmentStatus.REJECTED,
        )

        /**
         * Own-kind staff first, then hub managers standing in for a missing clerk; within each group
         * people it was not taken back from, then the least loaded.
         */
        internal val CANDIDATE_ORDER = compareBy<CandidateLoad>(
            { it.fallback },
            { it.avoided },
            { it.load.toDouble() / it.maximum },
            { it.load },
            { it.memberId },
        )

        /** Most post offices have no clerk; the hub manager takes the sorting work there. */
        fun eligibleKinds(requiredKind: WorkforceMemberKind): Set<WorkforceMemberKind> = when (requiredKind) {
            WorkforceMemberKind.WAREHOUSE_OPERATOR -> setOf(WorkforceMemberKind.WAREHOUSE_OPERATOR, WorkforceMemberKind.HUB_MANAGER)
            else -> setOf(requiredKind)
        }
    }
}
