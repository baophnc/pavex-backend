package com.pavex.operations.service

import com.pavex.common.api.OneBasedPagination
import com.pavex.common.api.PageResponse
import com.pavex.contracts.operations.OperationsHubSnapshot
import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.client.NetworkHubClient
import com.pavex.operations.dto.CreateWorkShiftRequest
import com.pavex.operations.dto.MyWorkShiftResponse
import com.pavex.operations.dto.UpdateWorkShiftRequest
import com.pavex.operations.dto.WorkShiftDetailResponse
import com.pavex.operations.dto.WorkShiftResponse
import com.pavex.operations.dto.WorkforceShiftAssignmentResponse
import com.pavex.operations.entity.WorkShift
import com.pavex.operations.entity.WorkShiftStatus
import com.pavex.operations.entity.WorkforceAvailabilityStatus
import com.pavex.operations.entity.WorkforceMember
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.entity.WorkforceShiftAssignment
import com.pavex.operations.entity.WorkforceShiftAssignmentStatus
import com.pavex.operations.exception.InvalidOperationsRequestException
import com.pavex.operations.exception.OperationsConflictException
import com.pavex.operations.exception.OperationsNotFoundException
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.WorkShiftRepository
import com.pavex.operations.repository.WorkforceAvailabilityRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import com.pavex.operations.repository.WorkforceShiftAssignmentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class WorkShiftService(
    private val shifts: WorkShiftRepository,
    private val roster: WorkforceShiftAssignmentRepository,
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
    private val availabilities: WorkforceAvailabilityRepository,
    private val identity: IdentityWorkforceClient,
    private val network: NetworkHubClient,
    private val dispatchQueue: AssignmentDispatchQueue,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun search(
        scope: WorkforceAccessScope,
        hubId: UUID?,
        status: WorkShiftStatus?,
        from: Instant?,
        to: Instant?,
        pageInput: Int,
        pageSizeInput: Int,
    ): PageResponse<WorkShiftResponse> {
        if (hubId != null && !scope.canAccessHub(hubId)) throw AccessDeniedException("Hub is outside the allowed scope")
        val pagination = OneBasedPagination.normalize(pageInput, pageSizeInput)
        if (!scope.isGlobal && scope.hubIds.isEmpty()) {
            return PageResponse(emptyList(), pagination.page, pagination.pageSize, 0)
        }
        val pageable = PageRequest.of(
            pagination.zeroBasedPage,
            pagination.pageSize,
            Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("code"), Sort.Order.asc("id")),
        )
        val result = shifts.searchVisible(
            globalAccess = scope.isGlobal,
            allowedHubIds = scope.hubIds.ifEmpty { setOf(QUERY_SENTINEL_UUID) },
            hubId = hubId,
            status = status,
            from = from,
            to = to,
            pageable = pageable,
        )
        val pageRows = result.content
        val counts = rosterCounts(pageRows.map { it.id })
        val hubs = network.snapshots(pageRows.map { it.hubId }).associateBy { it.hubId }
        return PageResponse(
            items = pageRows.map { response(it, counts[it.id] ?: 0, hubs[it.hubId]) },
            page = pagination.page,
            pageSize = pagination.pageSize,
            totalCount = result.totalElements,
        )
    }

    @Transactional(readOnly = true)
    fun get(scope: WorkforceAccessScope, shiftId: UUID): WorkShiftDetailResponse {
        val shift = findShift(shiftId)
        ensureShiftAccess(scope, shift)
        val assignments = roster.findAllByShiftId(shiftId)
        val memberById = members.findAllById(assignments.map { it.workforceMemberId }).associateBy { it.id }
        val hub = network.find(shift.hubId)
        return WorkShiftDetailResponse(
            shift = response(shift, assignments.count { it.status != WorkforceShiftAssignmentStatus.CANCELLED }, hub),
            roster = assignments
                .mapNotNull { assignment -> memberById[assignment.workforceMemberId]?.let { assignmentResponse(assignment, it) } }
                .sortedBy { it.employeeCode },
        )
    }

    @Transactional
    fun create(actorUserId: UUID, scope: WorkforceAccessScope, request: CreateWorkShiftRequest): WorkShiftResponse {
        if (!scope.canAccessHub(request.hubId)) throw AccessDeniedException("Hub is outside the allowed scope")
        val hub = network.find(request.hubId)
            ?: throw InvalidOperationsRequestException("hub_not_active", "The hub does not exist or is inactive.")
        if (hub.status != "ACTIVE") {
            throw InvalidOperationsRequestException("hub_not_active", "The hub does not exist or is inactive.")
        }
        val now = Instant.now(clock)
        val shift = try {
            WorkShift.create(request.hubId, request.name, request.startsAt, request.endsAt, actorUserId, now)
        } catch (exception: IllegalArgumentException) {
            throw invalidShift(exception)
        }
        try {
            shifts.saveAndFlush(shift)
        } catch (_: DataIntegrityViolationException) {
            throw OperationsConflictException("shift_conflict", "The shift conflicts with existing data.")
        }
        return response(shift, 0, hub)
    }

    @Transactional
    fun update(actorUserId: UUID, scope: WorkforceAccessScope, shiftId: UUID, request: UpdateWorkShiftRequest): WorkShiftResponse {
        requireActor(actorUserId)
        val shift = findShift(shiftId)
        ensureShiftAccess(scope, shift)
        if (hasRosterScheduleConflict(shift.id, request.startsAt, request.endsAt)) {
            throw InvalidOperationsRequestException(
                "shift_schedule_conflict",
                "The updated schedule overlaps another shift assigned to a roster member.",
            )
        }
        try {
            shift.updateSchedule(request.name, request.startsAt, request.endsAt, Instant.now(clock))
            shifts.saveAndFlush(shift)
        } catch (exception: IllegalArgumentException) {
            throw invalidShift(exception)
        } catch (exception: IllegalStateException) {
            throw InvalidOperationsRequestException("invalid_shift_state", exception.message ?: "Shift state is invalid.")
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw shiftChanged()
        }
        return response(shift, activeRosterCount(shift.id), network.find(shift.hubId))
    }

    @Transactional
    fun cancel(actorUserId: UUID, scope: WorkforceAccessScope, shiftId: UUID, reason: String): WorkShiftResponse {
        val shift = findShift(shiftId)
        ensureShiftAccess(scope, shift)
        val now = Instant.now(clock)
        val changed = try {
            shift.cancel(actorUserId, reason, now)
        } catch (exception: IllegalArgumentException) {
            throw invalidShift(exception)
        }
        if (changed) {
            val active = roster.findAllByShiftIdAndStatusIn(shiftId, ACTIVE_ROSTER_STATUSES)
            val checkedInIds = active.filter { it.status == WorkforceShiftAssignmentStatus.CHECKED_IN }
                .map { it.workforceMemberId }
            active.forEach { it.cancel(actorUserId, reason, now) }
            setOffDuty(checkedInIds, now)
            try {
                shifts.save(shift)
                roster.saveAll(active)
                shifts.flush()
            } catch (_: ObjectOptimisticLockingFailureException) {
                throw shiftChanged()
            }
        }
        return response(shift, activeRosterCount(shift.id), network.find(shift.hubId))
    }

    @Transactional
    fun assignMember(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        shiftId: UUID,
        workforceMemberId: UUID,
    ): WorkforceShiftAssignmentResponse {
        requireActor(actorUserId)
        val shift = findShift(shiftId)
        ensureShiftAccess(scope, shift)
        val now = Instant.now(clock)
        if (shift.status != WorkShiftStatus.SCHEDULED || !shift.startsAt.isAfter(now)) {
            throw InvalidOperationsRequestException(
                "shift_not_assignable",
                "Members can only be assigned to a scheduled shift before it starts.",
            )
        }
        val member = findMember(workforceMemberId)
        val eligibleAtHub = memberships.findByWorkforceMemberIdAndHubId(member.id, shift.hubId)?.isActive == true
        if (member.status != WorkforceMemberStatus.ACTIVE || !eligibleAtHub) {
            throw InvalidOperationsRequestException(
                "member_not_eligible_for_shift",
                "The workforce member must be active and assigned to the shift hub.",
            )
        }
        ensureIdentityEligible(member, "member_identity_access_revoked")

        val existing = roster.findByShiftIdAndWorkforceMemberId(shiftId, member.id)
        if (existing != null && existing.status in ACTIVE_ROSTER_STATUSES) return assignmentResponse(existing, member)
        if (existing != null && existing.status != WorkforceShiftAssignmentStatus.CANCELLED) {
            throw InvalidOperationsRequestException(
                "shift_assignment_completed",
                "A completed shift assignment cannot be assigned again.",
            )
        }
        if (hasOverlappingShift(member.id, shift.id, shift.startsAt, shift.endsAt)) {
            throw InvalidOperationsRequestException("shift_overlap", "The workforce member already has an overlapping shift.")
        }

        val assignment = existing ?: WorkforceShiftAssignment.create(shift.id, member.id, actorUserId, now)
        if (existing != null) {
            try {
                assignment.reassign(actorUserId, now)
            } catch (exception: IllegalStateException) {
                throw InvalidOperationsRequestException("invalid_shift_assignment", exception.message ?: "Shift assignment is invalid.")
            }
        }
        try {
            roster.saveAndFlush(assignment)
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw assignmentChanged()
        } catch (_: DataIntegrityViolationException) {
            throw assignmentChanged()
        }
        return assignmentResponse(assignment, member)
    }

    @Transactional
    fun removeMember(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        shiftId: UUID,
        workforceMemberId: UUID,
        reason: String?,
    ): WorkforceShiftAssignmentResponse {
        val shift = findShift(shiftId)
        ensureShiftAccess(scope, shift)
        val assignment = roster.findByShiftIdAndWorkforceMemberId(shiftId, workforceMemberId)
            ?: throw OperationsNotFoundException("shift_assignment_not_found", "The shift assignment was not found.")
        val member = findMember(workforceMemberId)
        val wasCheckedIn = assignment.status == WorkforceShiftAssignmentStatus.CHECKED_IN
        val now = Instant.now(clock)
        try {
            val changed = assignment.cancel(actorUserId, reason, now)
            if (changed) {
                if (wasCheckedIn) setOffDuty(listOf(workforceMemberId), now)
                roster.saveAndFlush(assignment)
            }
        } catch (exception: IllegalArgumentException) {
            throw InvalidOperationsRequestException("invalid_shift_assignment", exception.message ?: "Shift assignment is invalid.")
        } catch (exception: IllegalStateException) {
            throw InvalidOperationsRequestException("invalid_shift_assignment", exception.message ?: "Shift assignment is invalid.")
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw assignmentChanged()
        }
        return assignmentResponse(assignment, member)
    }

    @Transactional
    fun markAbsent(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        shiftId: UUID,
        workforceMemberId: UUID,
        note: String?,
    ): WorkforceShiftAssignmentResponse {
        val shift = findShift(shiftId)
        ensureShiftAccess(scope, shift)
        val assignment = roster.findByShiftIdAndWorkforceMemberId(shiftId, workforceMemberId)
            ?: throw OperationsNotFoundException("shift_assignment_not_found", "The shift assignment was not found.")
        val member = findMember(workforceMemberId)
        val now = Instant.now(clock)
        try {
            val changed = assignment.markAbsent(actorUserId, shift.startsAt, note, now)
            if (changed) {
                setOffDuty(listOf(workforceMemberId), now)
                roster.saveAndFlush(assignment)
            }
        } catch (exception: IllegalArgumentException) {
            throw InvalidOperationsRequestException("invalid_shift_assignment", exception.message ?: "Shift assignment is invalid.")
        } catch (exception: IllegalStateException) {
            throw InvalidOperationsRequestException("invalid_shift_assignment", exception.message ?: "Shift assignment is invalid.")
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw assignmentChanged()
        }
        return assignmentResponse(assignment, member)
    }

    @Transactional(readOnly = true)
    fun mine(userId: UUID, from: Instant?, to: Instant?): List<MyWorkShiftResponse> {
        val member = members.findByUserId(userId) ?: return emptyList()
        val now = Instant.now(clock)
        var rangeStart = from ?: now.minus(7, ChronoUnit.DAYS)
        var rangeEnd = to ?: now.plus(30, ChronoUnit.DAYS)
        if (rangeEnd < rangeStart) {
            val swap = rangeStart
            rangeStart = rangeEnd
            rangeEnd = swap
        }
        val assignments = roster.findAllByWorkforceMemberIdAndShiftWindow(member.id, rangeStart, rangeEnd)
        if (assignments.isEmpty()) return emptyList()
        val shiftById = shifts.findAllByIdIn(assignments.map { it.shiftId }).associateBy { it.id }
        val selected = assignments.mapNotNull { assignment ->
            val shift = shiftById[assignment.shiftId] ?: return@mapNotNull null
            if (shift.endsAt < rangeStart || shift.startsAt > rangeEnd) return@mapNotNull null
            assignment to shift
        }.sortedBy { it.second.startsAt }.take(200)
        val hubById = network.snapshots(selected.map { it.second.hubId }).associateBy { it.hubId }
        return selected.map { (assignment, shift) ->
            val hub = hubById[shift.hubId]
            MyWorkShiftResponse(
                shiftId = shift.id,
                code = shift.code,
                hubId = shift.hubId,
                hubCode = hub?.code,
                hubName = hub?.name,
                name = shift.name,
                startsAt = shift.startsAt,
                endsAt = shift.endsAt,
                shiftStatus = shift.status,
                assignmentStatus = assignment.status,
                checkedInAt = assignment.checkedInAt,
                checkedOutAt = assignment.checkedOutAt,
                markedAbsentAt = assignment.markedAbsentAt,
                cancelledAt = assignment.cancelledAt,
                note = assignment.note,
            )
        }
    }

    @Transactional
    fun checkIn(userId: UUID, shiftId: UUID): WorkforceShiftAssignmentResponse {
        val member = members.findByUserId(userId)
            ?: throw OperationsNotFoundException("workforce_member_not_found", "Workforce member was not found.")
        if (member.status != WorkforceMemberStatus.ACTIVE) {
            throw InvalidOperationsRequestException("member_not_active", "The workforce member is not active.")
        }
        val shift = findShift(shiftId)
        if (shift.status != WorkShiftStatus.SCHEDULED) {
            throw InvalidOperationsRequestException("shift_cancelled", "A cancelled shift cannot be checked in.")
        }
        if (memberships.findByWorkforceMemberIdAndHubId(member.id, shift.hubId)?.isActive != true) {
            throw InvalidOperationsRequestException(
                "member_not_eligible_for_shift",
                "The workforce member is no longer assigned to the shift hub.",
            )
        }
        ensureIdentityEligible(member, "identity_access_revoked")
        val assignment = roster.findByShiftIdAndWorkforceMemberId(shiftId, member.id)
            ?: throw OperationsNotFoundException("shift_assignment_not_found", "The shift assignment was not found.")
        val anotherCheckedIn = roster.findAllByWorkforceMemberIdAndStatusIn(
            member.id,
            listOf(WorkforceShiftAssignmentStatus.CHECKED_IN),
        ).any { it.shiftId != shiftId }
        if (anotherCheckedIn) {
            throw InvalidOperationsRequestException(
                "another_shift_checked_in",
                "Check out of the current shift before checking in to another shift.",
            )
        }
        val availability = availabilities.findById(member.id).orElseThrow {
            OperationsConflictException("availability_not_found", "Workforce availability was not found.")
        }
        val now = Instant.now(clock)
        try {
            assignment.checkIn(userId, shift.startsAt, shift.endsAt, now)
            availability.setStatus(WorkforceAvailabilityStatus.AVAILABLE, now)
            roster.save(assignment)
            availabilities.save(availability)
            roster.flush()
            dispatchQueue.wake(member.id)
        } catch (exception: IllegalStateException) {
            throw InvalidOperationsRequestException("invalid_check_in", exception.message ?: "Check-in is invalid.")
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw assignmentChanged()
        }
        return assignmentResponse(assignment, member)
    }

    @Transactional
    fun checkOut(userId: UUID, shiftId: UUID): WorkforceShiftAssignmentResponse {
        val member = members.findByUserId(userId)
            ?: throw OperationsNotFoundException("workforce_member_not_found", "Workforce member was not found.")
        val assignment = roster.findByShiftIdAndWorkforceMemberId(shiftId, member.id)
            ?: throw OperationsNotFoundException("shift_assignment_not_found", "The shift assignment was not found.")
        val now = Instant.now(clock)
        try {
            assignment.checkOut(userId, now)
            availabilities.findById(member.id).ifPresent { availability ->
                availability.setStatus(WorkforceAvailabilityStatus.OFF_DUTY, now)
                availabilities.save(availability)
            }
            roster.saveAndFlush(assignment)
            dispatchQueue.releaseUnaccepted(listOf(member.id), now)
        } catch (exception: IllegalStateException) {
            throw InvalidOperationsRequestException("invalid_check_out", exception.message ?: "Check-out is invalid.")
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw assignmentChanged()
        }
        return assignmentResponse(assignment, member)
    }

    private fun hasRosterScheduleConflict(shiftId: UUID, startsAt: Instant, endsAt: Instant): Boolean {
        val memberIds = roster.findAllByShiftIdAndStatusIn(shiftId, ACTIVE_ROSTER_STATUSES)
            .map { it.workforceMemberId }
            .distinct()
        return memberIds.any { hasOverlappingShift(it, shiftId, startsAt, endsAt) }
    }

    private fun hasOverlappingShift(workforceMemberId: UUID, shiftId: UUID, startsAt: Instant, endsAt: Instant): Boolean {
        val activeAssignments = roster.findAllByWorkforceMemberIdAndStatusIn(workforceMemberId, ACTIVE_ROSTER_STATUSES)
            .filter { it.shiftId != shiftId }
        if (activeAssignments.isEmpty()) return false
        return shifts.findAllByIdIn(activeAssignments.map { it.shiftId }).any { other ->
            other.status == WorkShiftStatus.SCHEDULED && other.startsAt < endsAt && other.endsAt > startsAt
        }
    }

    private fun ensureIdentityEligible(member: WorkforceMember, errorCode: String) {
        val snapshot = identity.snapshots(listOf(member.userId)).singleOrNull()
        val roles = snapshot?.roles?.toSet().orEmpty()
        val compatible = snapshot?.accountStatus == "ACTIVE" && WorkforceRolePolicy.isCompatible(member.kind, roles)
        if (!compatible) {
            throw InvalidOperationsRequestException(
                errorCode,
                "The workforce member's Identity account or compatible workforce role is not active.",
            )
        }
    }

    private fun setOffDuty(memberIds: Collection<UUID>, now: Instant) {
        memberIds.distinct().forEach { memberId ->
            availabilities.findById(memberId).ifPresent { availability ->
                availability.setStatus(WorkforceAvailabilityStatus.OFF_DUTY, now)
                availabilities.save(availability)
            }
        }
        // Work they had not accepted yet goes to whoever is still on shift.
        dispatchQueue.releaseUnaccepted(memberIds, now)
    }

    private fun rosterCounts(shiftIds: Collection<UUID>): Map<UUID, Int> =
        if (shiftIds.isEmpty()) emptyMap()
        else roster.findAllByShiftIdIn(shiftIds)
            .filter { it.status != WorkforceShiftAssignmentStatus.CANCELLED }
            .groupingBy { it.shiftId }
            .eachCount()

    private fun activeRosterCount(shiftId: UUID): Int =
        roster.findAllByShiftId(shiftId).count { it.status != WorkforceShiftAssignmentStatus.CANCELLED }

    private fun response(shift: WorkShift, rosterCount: Int, hub: OperationsHubSnapshot?): WorkShiftResponse = WorkShiftResponse(
        id = shift.id,
        hubId = shift.hubId,
        hubCode = hub?.code,
        hubName = hub?.name,
        code = shift.code,
        name = shift.name,
        startsAt = shift.startsAt,
        endsAt = shift.endsAt,
        status = shift.status,
        rosterCount = rosterCount,
        createdByUserId = shift.createdByUserId,
        cancelledByUserId = shift.cancelledByUserId,
        cancelReason = shift.cancelReason,
        cancelledAt = shift.cancelledAt,
        createdAt = shift.createdAt,
        updatedAt = shift.updatedAt,
    )

    private fun assignmentResponse(assignment: WorkforceShiftAssignment, member: WorkforceMember) = WorkforceShiftAssignmentResponse(
        id = assignment.id,
        shiftId = assignment.shiftId,
        workforceMemberId = assignment.workforceMemberId,
        employeeCode = member.employeeCode,
        kind = member.kind,
        status = assignment.status,
        assignedByUserId = assignment.assignedByUserId,
        statusChangedByUserId = assignment.statusChangedByUserId,
        assignedAt = assignment.assignedAt,
        checkedInAt = assignment.checkedInAt,
        checkedOutAt = assignment.checkedOutAt,
        markedAbsentAt = assignment.markedAbsentAt,
        cancelledAt = assignment.cancelledAt,
        note = assignment.note,
        updatedAt = assignment.updatedAt,
    )

    private fun findShift(shiftId: UUID): WorkShift = shifts.findById(shiftId).orElseThrow {
        OperationsNotFoundException("shift_not_found", "The shift was not found.")
    }

    private fun findMember(memberId: UUID): WorkforceMember = members.findById(memberId).orElseThrow {
        OperationsNotFoundException("workforce_member_not_found", "Workforce member was not found.")
    }

    private fun ensureShiftAccess(scope: WorkforceAccessScope, shift: WorkShift) {
        if (!scope.canAccessHub(shift.hubId)) {
            throw OperationsNotFoundException("shift_not_found", "The shift was not found.")
        }
    }

    private fun requireActor(actorUserId: UUID) {
        if (actorUserId == UUID(0, 0)) throw InvalidOperationsRequestException("invalid_actor", "Actor is required.")
    }

    private fun invalidShift(exception: IllegalArgumentException) =
        InvalidOperationsRequestException("invalid_shift", exception.message ?: "Shift is invalid.")

    private fun shiftChanged() = OperationsConflictException(
        "shift_changed",
        "The shift changed concurrently. Reload and try again.",
    )

    private fun assignmentChanged() = OperationsConflictException(
        "shift_assignment_changed",
        "The shift assignment changed concurrently. Reload and try again.",
    )

    companion object {
        private val QUERY_SENTINEL_UUID: UUID = UUID(0, 1)
        private val ACTIVE_ROSTER_STATUSES = listOf(
            WorkforceShiftAssignmentStatus.SCHEDULED,
            WorkforceShiftAssignmentStatus.CHECKED_IN,
        )
    }
}
