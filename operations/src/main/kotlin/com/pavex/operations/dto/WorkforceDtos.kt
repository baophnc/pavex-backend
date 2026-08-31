package com.pavex.operations.dto

import com.pavex.contracts.operations.OperationsHubSnapshot
import com.pavex.contracts.operations.WorkforceIdentitySnapshot
import com.pavex.operations.entity.WorkforceAuditAction
import com.pavex.operations.entity.WorkforceAvailabilityStatus
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.entity.WorkforceMemberStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateWorkforceMemberRequest(
    val userId: UUID,
    val kind: WorkforceMemberKind,
)

data class SetWorkforceStatusRequest(
    val status: WorkforceMemberStatus,
    val reason: String? = null,
)

data class SetWorkforceCapacityRequest(
    val maxConcurrentAssignments: Int,
    val reason: String? = null,
)

data class AssignWorkforceHubRequest(
    val hubId: UUID,
    val isPrimary: Boolean = false,
    val reason: String? = null,
)

data class WorkforceMemberResponse(
    val id: UUID,
    val userId: UUID,
    val employeeCode: String,
    val kind: WorkforceMemberKind,
    val status: WorkforceMemberStatus,
    val identity: WorkforceIdentityResponse?,
    val identityAccessValid: Boolean,
    val availability: WorkforceAvailabilityResponse?,
    val hubMemberships: List<WorkforceHubMembershipResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class WorkforceIdentityResponse(
    val userId: UUID,
    val email: String,
    val emailVerified: Boolean,
    val accountStatus: String,
    val profileStatus: String,
    val firstName: String?,
    val lastName: String?,
    val displayName: String?,
    val gender: String?,
    val dateOfBirth: LocalDate?,
    val avatarUrl: String?,
    val roles: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(snapshot: WorkforceIdentitySnapshot) = WorkforceIdentityResponse(
            userId = snapshot.userId,
            email = snapshot.email,
            emailVerified = snapshot.emailVerified,
            accountStatus = snapshot.accountStatus,
            profileStatus = snapshot.profileStatus,
            firstName = snapshot.firstName,
            lastName = snapshot.lastName,
            displayName = snapshot.displayName,
            gender = snapshot.gender,
            dateOfBirth = snapshot.dateOfBirth,
            avatarUrl = snapshot.avatarUrl,
            roles = snapshot.roles,
            createdAt = snapshot.createdAt,
            updatedAt = snapshot.updatedAt,
        )
    }
}

data class WorkforceAvailabilityResponse(
    val status: WorkforceAvailabilityStatus,
    val maxConcurrentAssignments: Int,
    val activeAssignments: Int,
    val remainingCapacity: Int,
    val updatedAt: Instant,
)

data class WorkforceHubMembershipResponse(
    val id: UUID,
    val hubId: UUID,
    val hubCode: String?,
    val hubName: String?,
    val hubType: String?,
    val hubStatus: String?,
    val addressLine: String?,
    val provinceCode: String?,
    val wardCode: String?,
    val parentHubId: UUID?,
    val isPrimary: Boolean,
    val isActive: Boolean,
    val assignedAt: Instant,
    val unassignedAt: Instant?,
    val updatedAt: Instant,
) {
    companion object {
        fun hubFields(snapshot: OperationsHubSnapshot?) = HubFields(
            code = snapshot?.code,
            name = snapshot?.name,
            type = snapshot?.type,
            status = snapshot?.status,
            addressLine = snapshot?.addressLine,
            provinceCode = snapshot?.provinceCode,
            wardCode = snapshot?.wardCode,
            parentHubId = snapshot?.parentHubId,
        )
    }

    data class HubFields(
        val code: String?,
        val name: String?,
        val type: String?,
        val status: String?,
        val addressLine: String?,
        val provinceCode: String?,
        val wardCode: String?,
        val parentHubId: UUID?,
    )
}

data class WorkforceAuditResponse(
    val id: UUID,
    val workforceMemberId: UUID,
    val hubId: UUID?,
    val actorUserId: UUID,
    val action: WorkforceAuditAction,
    val previousValue: String?,
    val newValue: String?,
    val reason: String?,
    val occurredAt: Instant,
)
