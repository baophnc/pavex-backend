package com.pavex.operations.service

import com.pavex.common.api.OneBasedPagination
import com.pavex.common.api.PageResponse
import com.pavex.contracts.operations.OperationsHubSnapshot
import com.pavex.contracts.operations.WorkforceIdentitySnapshot
import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.client.NetworkHubClient
import com.pavex.operations.dto.AssignWorkforceHubRequest
import com.pavex.operations.dto.CreateWorkforceMemberRequest
import com.pavex.operations.dto.SetWorkforceCapacityRequest
import com.pavex.operations.dto.SetWorkforceStatusRequest
import com.pavex.operations.dto.WorkforceAuditResponse
import com.pavex.operations.dto.WorkforceAvailabilityResponse
import com.pavex.operations.dto.WorkforceHubMembershipResponse
import com.pavex.operations.dto.WorkforceIdentityResponse
import com.pavex.operations.dto.WorkforceMemberResponse
import com.pavex.operations.entity.HubMembership
import com.pavex.operations.entity.OperationalAssignmentStatus
import com.pavex.operations.entity.WorkShiftStatus
import com.pavex.operations.entity.WorkforceAuditAction
import com.pavex.operations.entity.WorkforceAuditLog
import com.pavex.operations.entity.WorkforceAvailability
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
import com.pavex.operations.repository.WorkforceAuditLogRepository
import com.pavex.operations.repository.WorkforceAvailabilityRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import com.pavex.operations.repository.WorkforceShiftAssignmentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkforceService(
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
    private val availabilities: WorkforceAvailabilityRepository,
    private val audits: WorkforceAuditLogRepository,
    private val assignments: OperationalAssignmentRepository,
    private val shiftAssignments: WorkforceShiftAssignmentRepository,
    private val shifts: WorkShiftRepository,
    private val identity: IdentityWorkforceClient,
    private val network: NetworkHubClient,
) {
    @Transactional(readOnly = true)
    fun search(
        scope: WorkforceAccessScope,
        search: String?,
        status: WorkforceMemberStatus?,
        kind: WorkforceMemberKind?,
        hubId: UUID?,
        pageInput: Int,
        pageSizeInput: Int,
    ): PageResponse<WorkforceMemberResponse> {
        if (hubId != null && !scope.canAccessHub(hubId)) throw AccessDeniedException("Hub is outside the allowed scope")
        val pagination = OneBasedPagination.normalize(pageInput, pageSizeInput)
        val all = members.findAllByOrderByEmployeeCodeAsc()
        if (all.isEmpty()) return PageResponse(emptyList(), pagination.page, pagination.pageSize, 0)

        val membershipsByMember = memberships.findAllByWorkforceMemberIdIn(all.map { it.id }).groupBy { it.workforceMemberId }
        var filtered = all.asSequence()
            .filter { status == null || it.status == status }
            .filter { kind == null || it.kind == kind }
            .filter { member ->
                val activeHubs = membershipsByMember[member.id].orEmpty().filter { it.isActive }.map { it.hubId }.toSet()
                (scope.isGlobal || activeHubs.any(scope.hubIds::contains)) &&
                    (hubId == null || hubId in activeHubs)
            }
            .toList()

        val needle = search?.trim()?.takeIf { it.isNotBlank() }
        if (needle != null && filtered.isNotEmpty()) {
            val identityMatches = identity.snapshots(filtered.map { it.userId }, needle).map { it.userId }.toSet()
            filtered = filtered.filter { member ->
                member.employeeCode.contains(needle, ignoreCase = true) || member.userId in identityMatches
            }
        }

        val total = filtered.size.toLong()
        val fromIndex = ((pagination.page - 1) * pagination.pageSize).coerceAtMost(filtered.size)
        val toIndex = (fromIndex + pagination.pageSize).coerceAtMost(filtered.size)
        val pageMembers = filtered.subList(fromIndex, toIndex)
        return PageResponse(
            items = enrich(pageMembers, scope, membershipsByMember),
            page = pagination.page,
            pageSize = pagination.pageSize,
            totalCount = total,
        )
    }

    @Transactional(readOnly = true)
    fun get(scope: WorkforceAccessScope, memberId: UUID): WorkforceMemberResponse {
        val member = members.findById(memberId).orElseThrow {
            OperationsNotFoundException("workforce_member_not_found", "Workforce member was not found.")
        }
        val memberMemberships = memberships.findAllByWorkforceMemberId(member.id)
        ensureMemberAccess(scope, memberMemberships)
        return enrich(listOf(member), scope, mapOf(member.id to memberMemberships)).single()
    }

    @Transactional
    fun create(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        request: CreateWorkforceMemberRequest,
    ): WorkforceMemberResponse {
        requireGlobal(scope)
        if (members.existsByUserId(request.userId)) {
            throw OperationsConflictException("workforce_member_exists", "This user already has a workforce member record.")
        }
        val identitySnapshot = identity.snapshots(listOf(request.userId)).singleOrNull()
            ?: throw OperationsNotFoundException("identity_user_not_found", "Identity user was not found.")
        validateIdentity(request.kind, identitySnapshot)

        val now = Instant.now()
        val member = WorkforceMember.create(request.userId, request.kind, now)
        try {
            members.saveAndFlush(member)
            availabilities.save(
                WorkforceAvailability(
                    workforceMemberId = member.id,
                    status = WorkforceAvailabilityStatus.OFF_DUTY,
                    maxConcurrentAssignments = defaultMaximumAssignments(request.kind),
                    updatedAt = now,
                ),
            )
            audit(member.id, null, actorUserId, WorkforceAuditAction.MEMBER_CREATED, null, request.kind.name, null, now)
        } catch (_: DataIntegrityViolationException) {
            throw OperationsConflictException("workforce_member_exists", "This user already has a workforce member record.")
        }
        return enrich(listOf(member), scope, mapOf(member.id to emptyList()), mapOf(member.userId to identitySnapshot)).single()
    }

    @Transactional
    fun setStatus(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        memberId: UUID,
        request: SetWorkforceStatusRequest,
    ): WorkforceMemberResponse {
        requireGlobal(scope)
        validateReason(request.reason)
        if (request.status != WorkforceMemberStatus.ACTIVE && request.reason.isNullOrBlank()) {
            throw InvalidOperationsRequestException("status_reason_required", "A reason is required when suspending or offboarding a member.")
        }
        val member = findMember(memberId)
        val previous = member.status
        if (previous == request.status) return get(scope, memberId)
        val now = Instant.now()

        if (request.status != WorkforceMemberStatus.ACTIVE) {
            val activeAssignments = assignments.findAllByAssigneeWorkforceMemberIdAndStatusIn(
                member.id,
                OperationalAssignmentService.ACTIVE_STATUSES,
            )
            if (activeAssignments.any { it.status == OperationalAssignmentStatus.IN_PROGRESS }) {
                throw OperationsConflictException(
                    "member_has_in_progress_assignments",
                    "Complete or hand over in-progress assignments before suspending or offboarding this member.",
                )
            }
            activeAssignments
                .filter { it.status == OperationalAssignmentStatus.ASSIGNED || it.status == OperationalAssignmentStatus.ACCEPTED }
                .forEach { it.requeue(now) }

            val rosterRows = shiftAssignments.findAllByWorkforceMemberIdAndStatusIn(
                member.id,
                listOf(WorkforceShiftAssignmentStatus.SCHEDULED, WorkforceShiftAssignmentStatus.CHECKED_IN),
            )
            val shiftById = shifts.findAllByIdIn(rosterRows.map { it.shiftId }.distinct()).associateBy { it.id }
            rosterRows.filter { row ->
                val shift = shiftById[row.shiftId]
                shift != null && shift.status == WorkShiftStatus.SCHEDULED && shift.endsAt > now
            }.forEach { row -> row.cancel(actorUserId, request.reason, now) }
        }

        try {
            member.changeStatus(request.status, now)
            if (request.status != WorkforceMemberStatus.ACTIVE) {
                availabilities.findById(member.id).ifPresent { availability ->
                    availability.setStatus(WorkforceAvailabilityStatus.OFF_DUTY, now)
                    availabilities.save(availability)
                }
            }
            if (request.status == WorkforceMemberStatus.OFFBOARDED) {
                memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id).forEach { membership ->
                    val wasPrimary = membership.isPrimary
                    membership.deactivate(now)
                    memberships.save(membership)
                    audit(
                        member.id,
                        membership.hubId,
                        actorUserId,
                        WorkforceAuditAction.HUB_UNASSIGNED,
                        if (wasPrimary) "ActivePrimary" else "Active",
                        "Inactive",
                        "Member offboarded.",
                        now,
                    )
                }
            }
            assignments.flush()
            shiftAssignments.flush()
            members.saveAndFlush(member)
            audit(member.id, null, actorUserId, WorkforceAuditAction.STATUS_CHANGED, previous.name, request.status.name, request.reason, now)
        } catch (exception: IllegalStateException) {
            throw InvalidOperationsRequestException("invalid_workforce_status", exception.message ?: "Workforce status change is invalid.")
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw OperationsConflictException("workforce_changed", "Workforce data changed while the request was being processed.")
        }
        return get(scope, member.id)
    }

    @Transactional
    fun setCapacity(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        memberId: UUID,
        request: SetWorkforceCapacityRequest,
    ): WorkforceMemberResponse {
        requireGlobal(scope)
        validateReason(request.reason)
        if (request.maxConcurrentAssignments !in 1..100) {
            throw InvalidOperationsRequestException("invalid_capacity", "Maximum concurrent assignments must be between 1 and 100.")
        }
        val member = findMember(memberId)
        val availability = availabilities.findById(member.id).orElseThrow {
            OperationsConflictException("workforce_availability_not_found", "Workforce availability is not configured for this member.")
        }
        val previous = availability.maxConcurrentAssignments
        if (previous == request.maxConcurrentAssignments) return get(scope, memberId)
        val now = Instant.now()
        availability.setMaximum(request.maxConcurrentAssignments, now)
        availabilities.saveAndFlush(availability)
        audit(member.id, null, actorUserId, WorkforceAuditAction.CAPACITY_CHANGED, previous.toString(), request.maxConcurrentAssignments.toString(), request.reason, now)
        return get(scope, member.id)
    }

    @Transactional
    fun assignHub(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        memberId: UUID,
        request: AssignWorkforceHubRequest,
    ): WorkforceMemberResponse {
        validateReason(request.reason)
        if (!scope.canAccessHub(request.hubId)) throw AccessDeniedException("Hub is outside the allowed scope")
        if (!scope.isGlobal && request.isPrimary) throw AccessDeniedException("Only global workforce managers may set a primary hub")

        val member = findMember(memberId)
        if (member.status != WorkforceMemberStatus.ACTIVE) {
            throw InvalidOperationsRequestException("member_not_active", "Only active workforce members can be assigned to hubs.")
        }
        val hub = network.find(request.hubId)
            ?: throw InvalidOperationsRequestException("hub_not_found", "The hub does not exist in Network.")
        if (hub.status != ACTIVE_HUB_STATUS) {
            throw InvalidOperationsRequestException("hub_not_active", "The hub is not active.")
        }

        val current = memberships.findByWorkforceMemberIdAndHubId(member.id, request.hubId)
        if (current?.isActive == true && current.isPrimary == request.isPrimary) return get(scope, member.id)
        val previousState = current?.let { membershipState(it) }
        val now = Instant.now()

        if (request.isPrimary) {
            val currentPrimaries = memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id)
                .filter { it.isPrimary && it.hubId != request.hubId }
            if (currentPrimaries.isNotEmpty()) {
                currentPrimaries.forEach { it.setPrimary(false, now) }
                memberships.saveAll(currentPrimaries)
                // The partial unique index allows one active primary hub. Flush the
                // demotion before promoting another membership to avoid transient
                // unique-constraint failures inside the same transaction.
                memberships.flush()
            }
        }

        val membership = current ?: HubMembership(
            workforceMemberId = member.id,
            hubId = request.hubId,
            isPrimary = request.isPrimary,
            isActive = true,
            assignedAt = now,
            updatedAt = now,
        )
        if (current != null) current.reactivate(request.isPrimary, now)
        memberships.saveAndFlush(membership)
        member.updatedAt = now
        members.save(member)
        audit(
            member.id,
            request.hubId,
            actorUserId,
            WorkforceAuditAction.HUB_ASSIGNED,
            previousState,
            if (request.isPrimary) "ActivePrimary" else "Active",
            request.reason,
            now,
        )
        return get(scope, member.id)
    }

    @Transactional
    fun unassignHub(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        memberId: UUID,
        hubId: UUID,
        reason: String?,
    ): WorkforceMemberResponse {
        validateReason(reason)
        if (!scope.canAccessHub(hubId)) throw AccessDeniedException("Hub is outside the allowed scope")
        val member = findMember(memberId)
        val membership = memberships.findByWorkforceMemberIdAndHubId(member.id, hubId)
            ?: return get(scope, member.id)
        if (!membership.isActive) return get(scope, member.id)

        val hubAssignments = assignments.findAllByAssigneeWorkforceMemberIdAndHubIdAndStatusIn(
            member.id,
            hubId,
            OperationalAssignmentService.ACTIVE_STATUSES,
        )
        if (hubAssignments.any { it.status == OperationalAssignmentStatus.IN_PROGRESS }) {
            throw OperationsConflictException(
                "member_has_in_progress_hub_assignments",
                "Complete or hand over in-progress assignments at this hub before removing the member from it.",
            )
        }

        val now = Instant.now()
        hubAssignments.forEach { it.requeue(now) }
        val rosterRows = shiftAssignments.findAllByWorkforceMemberIdAndStatusIn(
            member.id,
            listOf(WorkforceShiftAssignmentStatus.SCHEDULED, WorkforceShiftAssignmentStatus.CHECKED_IN),
        )
        val shiftById = shifts.findAllByIdIn(rosterRows.map { it.shiftId }.distinct()).associateBy { it.id }
        val relevantRoster = rosterRows.filter { row ->
            val shift = shiftById[row.shiftId]
            shift != null && shift.hubId == hubId && shift.status == WorkShiftStatus.SCHEDULED && shift.endsAt > now
        }
        val hadCheckedInShift = relevantRoster.any { it.status == WorkforceShiftAssignmentStatus.CHECKED_IN }
        relevantRoster.forEach { it.cancel(actorUserId, reason, now) }
        if (hadCheckedInShift) {
            availabilities.findById(member.id).ifPresent { availability ->
                availability.setStatus(WorkforceAvailabilityStatus.OFF_DUTY, now)
                availabilities.save(availability)
            }
        }

        val previousState = membershipState(membership)
        membership.deactivate(now)
        memberships.saveAndFlush(membership)
        assignments.flush()
        shiftAssignments.flush()
        member.updatedAt = now
        members.save(member)
        audit(member.id, hubId, actorUserId, WorkforceAuditAction.HUB_UNASSIGNED, previousState, "Inactive", reason, now)
        return get(scope, member.id)
    }

    @Transactional(readOnly = true)
    fun audit(scope: WorkforceAccessScope, memberId: UUID): List<WorkforceAuditResponse> {
        val member = findMember(memberId)
        val memberMemberships = memberships.findAllByWorkforceMemberId(member.id)
        ensureMemberAccess(scope, memberMemberships)
        return audits.findAllByWorkforceMemberIdOrderByOccurredAtDesc(member.id)
            .asSequence()
            .filter { scope.isGlobal || (it.hubId != null && it.hubId in scope.hubIds) }
            .map { row ->
                WorkforceAuditResponse(
                    id = row.id,
                    workforceMemberId = row.workforceMemberId,
                    hubId = row.hubId,
                    actorUserId = row.actorUserId,
                    action = row.action,
                    previousValue = row.previousValue,
                    newValue = row.newValue,
                    reason = row.reason,
                    occurredAt = row.occurredAt,
                )
            }
            .toList()
    }

    private fun enrich(
        pageMembers: List<WorkforceMember>,
        scope: WorkforceAccessScope,
        membershipsByMemberInput: Map<UUID, List<HubMembership>>? = null,
        identityByUserInput: Map<UUID, WorkforceIdentitySnapshot>? = null,
    ): List<WorkforceMemberResponse> {
        if (pageMembers.isEmpty()) return emptyList()
        val memberIds = pageMembers.map { it.id }
        val membershipsByMember = membershipsByMemberInput ?: memberships.findAllByWorkforceMemberIdIn(memberIds).groupBy { it.workforceMemberId }
        val visibleMemberships = membershipsByMember.mapValues { (_, rows) ->
            rows.filter { scope.isGlobal || it.hubId in scope.hubIds }
        }
        val identityByUser = identityByUserInput ?: identity.snapshots(pageMembers.map { it.userId }).associateBy { it.userId }
        val availabilityByMember = availabilities.findAllByWorkforceMemberIdIn(memberIds).associateBy { it.workforceMemberId }
        val activeAssignmentsByMember = assignments.findAllByAssigneeWorkforceMemberIdInAndStatusIn(
            memberIds,
            OperationalAssignmentService.ACTIVE_STATUSES,
        ).groupingBy { requireNotNull(it.assigneeWorkforceMemberId) }.eachCount()
        val hubIds = visibleMemberships.values.flatten().map { it.hubId }.distinct()
        val hubById = network.snapshots(hubIds).associateBy { it.hubId }

        return pageMembers.map { member ->
            val identitySnapshot = identityByUser[member.userId]
            val availability = availabilityByMember[member.id]
            WorkforceMemberResponse(
                id = member.id,
                userId = member.userId,
                employeeCode = member.employeeCode,
                kind = member.kind,
                status = member.status,
                identity = identitySnapshot?.let(WorkforceIdentityResponse::from),
                identityAccessValid = identitySnapshot?.let { isIdentityCompatible(member.kind, it) } == true,
                availability = availability?.let {
                    val activeAssignments = activeAssignmentsByMember.getOrDefault(member.id, 0)
                    WorkforceAvailabilityResponse(
                        status = it.status,
                        maxConcurrentAssignments = it.maxConcurrentAssignments,
                        activeAssignments = activeAssignments,
                        remainingCapacity = (it.maxConcurrentAssignments - activeAssignments).coerceAtLeast(0),
                        updatedAt = it.updatedAt,
                    )
                },
                hubMemberships = visibleMemberships[member.id].orEmpty()
                    .sortedWith(compareByDescending<HubMembership> { it.isActive }.thenByDescending { it.isPrimary }.thenBy { it.assignedAt })
                    .map { membership -> membershipResponse(membership, hubById[membership.hubId]) },
                createdAt = member.createdAt,
                updatedAt = member.updatedAt,
            )
        }
    }

    private fun membershipResponse(membership: HubMembership, hub: OperationsHubSnapshot?): WorkforceHubMembershipResponse {
        val fields = WorkforceHubMembershipResponse.hubFields(hub)
        return WorkforceHubMembershipResponse(
            id = membership.id,
            hubId = membership.hubId,
            hubCode = fields.code,
            hubName = fields.name,
            hubType = fields.type,
            hubStatus = fields.status,
            addressLine = fields.addressLine,
            provinceCode = fields.provinceCode,
            wardCode = fields.wardCode,
            parentHubId = fields.parentHubId,
            isPrimary = membership.isPrimary,
            isActive = membership.isActive,
            assignedAt = membership.assignedAt,
            unassignedAt = membership.unassignedAt,
            updatedAt = membership.updatedAt,
        )
    }

    private fun validateIdentity(kind: WorkforceMemberKind, snapshot: WorkforceIdentitySnapshot) {
        if (snapshot.accountStatus != "ACTIVE") {
            throw InvalidOperationsRequestException("user_not_active", "The Identity account is not active.")
        }
        if (!isIdentityCompatible(kind, snapshot)) {
            throw InvalidOperationsRequestException("incompatible_user_role", "The user does not have a role compatible with $kind.")
        }
    }

    private fun isIdentityCompatible(kind: WorkforceMemberKind, snapshot: WorkforceIdentitySnapshot): Boolean {
        val roles = snapshot.roles.toSet()
        return when (kind) {
            WorkforceMemberKind.WAREHOUSE_OPERATOR -> "WAREHOUSE_STAFF" in roles
            WorkforceMemberKind.COURIER, WorkforceMemberKind.LINE_HAUL_DRIVER -> "DRIVER" in roles
            WorkforceMemberKind.DISPATCHER -> "OPERATIONS_MANAGER" in roles || "HUB_MANAGER" in roles
        }
    }

    private fun defaultMaximumAssignments(kind: WorkforceMemberKind): Int = when (kind) {
        WorkforceMemberKind.COURIER -> 8
        WorkforceMemberKind.WAREHOUSE_OPERATOR -> 30
        WorkforceMemberKind.DISPATCHER -> 10
        WorkforceMemberKind.LINE_HAUL_DRIVER -> 2
    }

    private fun ensureMemberAccess(scope: WorkforceAccessScope, memberMemberships: Collection<HubMembership>) {
        if (scope.isGlobal) return
        val visible = memberMemberships.any { it.isActive && it.hubId in scope.hubIds }
        if (!visible) throw OperationsNotFoundException("workforce_member_not_found", "Workforce member was not found.")
    }

    private fun requireGlobal(scope: WorkforceAccessScope) {
        if (!scope.isGlobal) throw AccessDeniedException("Global workforce permission is required")
    }

    private fun findMember(memberId: UUID): WorkforceMember = members.findById(memberId).orElseThrow {
        OperationsNotFoundException("workforce_member_not_found", "Workforce member was not found.")
    }

    private fun audit(
        memberId: UUID,
        hubId: UUID?,
        actorUserId: UUID,
        action: WorkforceAuditAction,
        previousValue: String?,
        newValue: String?,
        reason: String?,
        now: Instant,
    ) {
        audits.save(
            WorkforceAuditLog(
                workforceMemberId = memberId,
                hubId = hubId,
                actorUserId = actorUserId,
                action = action,
                previousValue = previousValue,
                newValue = newValue,
                reason = reason?.trim()?.takeIf { it.isNotBlank() },
                occurredAt = now,
            ),
        )
    }

    private fun validateReason(reason: String?) {
        if (reason?.trim()?.length ?: 0 > 500) {
            throw InvalidOperationsRequestException("invalid_reason", "Reason must not exceed 500 characters.")
        }
    }

    private fun membershipState(membership: HubMembership): String = when {
        !membership.isActive -> "Inactive"
        membership.isPrimary -> "ActivePrimary"
        else -> "Active"
    }

    companion object {
        private const val ACTIVE_HUB_STATUS = "ACTIVE"
    }
}
