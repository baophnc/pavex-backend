package com.pavex.operations.controller

import com.pavex.contracts.fleet.FleetHubScopeSnapshot
import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.operations.entity.OperationalAssignmentType
import com.pavex.operations.entity.WorkforceMemberStatus
import com.pavex.operations.repository.HubMembershipRepository
import com.pavex.operations.repository.WorkforceMemberRepository
import com.pavex.operations.service.OperationalAssignmentService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Read-only views Fleet needs to build line-haul trips and to scope who may watch them. */
@Hidden
@RestController
@RequestMapping("/internal/v1/operations/fleet")
class InternalFleetOperationsController(
    private val assignments: OperationalAssignmentService,
    private val members: WorkforceMemberRepository,
    private val memberships: HubMembershipRepository,
) {
    /** Open line-haul assignments of one driver; each carries the route leg it runs on. */
    @GetMapping("/users/{userId}/line-haul-assignments")
    fun lineHaulAssignments(@PathVariable userId: UUID): List<WorkforceAssignmentSnapshot> =
        assignments.mine(userId)
            .filter { it.type == OperationalAssignmentType.LINE_HAUL }
            .map { value ->
                WorkforceAssignmentSnapshot(
                    id = value.id,
                    code = value.code,
                    shipmentId = value.shipmentId,
                    sequence = value.sequence,
                    routeLegId = value.routeLegId,
                    type = OperationsAssignmentType.LINE_HAUL,
                    hubId = value.hubId,
                    status = OperationsAssignmentStatus.valueOf(value.status.name),
                    priority = value.priority,
                    assignedAt = value.assignedAt,
                    acceptedAt = value.acceptedAt,
                    startedAt = value.startedAt,
                )
            }

    /** Hubs the user is an active member of; empty when they are not active workforce. */
    @GetMapping("/users/{userId}/hub-scope")
    @Transactional(readOnly = true)
    fun hubScope(@PathVariable userId: UUID): FleetHubScopeSnapshot {
        val member = members.findByUserId(userId)?.takeIf { it.status == WorkforceMemberStatus.ACTIVE }
            ?: return FleetHubScopeSnapshot(emptyList())
        return FleetHubScopeSnapshot(
            memberships.findAllByWorkforceMemberIdAndIsActiveTrue(member.id).map { it.hubId }.distinct(),
        )
    }
}
