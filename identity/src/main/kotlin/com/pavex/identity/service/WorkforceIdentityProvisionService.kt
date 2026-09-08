package com.pavex.identity.service

import com.pavex.contracts.operations.ProvisionWorkforceIdentityRequest
import com.pavex.contracts.operations.WorkforceIdentitySnapshot
import com.pavex.contracts.operations.WorkforceIdentitySnapshotRequest
import com.pavex.identity.dto.admin.CreateUserRequest
import com.pavex.identity.entity.Gender
import com.pavex.identity.exception.InvalidUserProfileException
import com.pavex.identity.repository.UserAccountRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class WorkforceIdentityProvisionService(
    private val accounts: UserAccountRepository,
    private val adminUsers: AdminUserService,
    private val lookup: WorkforceIdentityLookupService,
) {
    @Transactional
    fun provision(request: ProvisionWorkforceIdentityRequest): WorkforceIdentitySnapshot {
        val roleCode = request.roleCode.trim().uppercase()
        if (roleCode !in ALLOWED_WORKFORCE_ROLES) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Role is not allowed for workforce provisioning")
        }
        val firstName = required(request.firstName, "firstName")
        val lastName = required(request.lastName, "lastName")
        val gender = runCatching { Gender.valueOf(request.gender.trim().uppercase()) }.getOrElse {
            throw InvalidUserProfileException()
        }
        if (gender == Gender.UNSPECIFIED) throw InvalidUserProfileException()

        val email = request.email.trim().lowercase()
        val existing = accounts.findByEmail(email)
        val userId = if (existing != null) {
            val snapshot = lookup.snapshots(WorkforceIdentitySnapshotRequest(listOf(existing.id))).singleOrNull()
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Existing Identity user cannot be loaded")
            if (roleCode !in snapshot.roles) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already belongs to a different account. Use a new workforce email instead of silently changing its role.",
                )
            }
            if (snapshot.accountStatus != "ACTIVE" || snapshot.profileStatus != "COMPLETED") {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Existing workforce Identity account must be ACTIVE with a completed profile before onboarding can resume.",
                )
            }
            existing.id
        } else {
            adminUsers.createWorkforceUser(
                request.actorUserId,
                CreateUserRequest(
                    email = email,
                    roleCode = roleCode,
                    firstName = firstName,
                    lastName = lastName,
                    displayName = request.displayName,
                    gender = gender,
                    dateOfBirth = request.dateOfBirth,
                    avatarUrl = request.avatarUrl,
                ),
            ).userId
        }

        return lookup.snapshots(WorkforceIdentitySnapshotRequest(listOf(userId))).singleOrNull()
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Provisioned workforce Identity snapshot is unavailable")
    }

    private fun required(value: String, field: String): String = value.trim().takeIf { it.isNotEmpty() }
        ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "$field is required")

    companion object {
        private val ALLOWED_WORKFORCE_ROLES = setOf(
            "COURIER",
            "LINE_HAUL_DRIVER",
            "WAREHOUSE_OPERATOR",
            "HUB_DISPATCHER",
            "DRIVER",
            "WAREHOUSE_STAFF",
            "HUB_MANAGER",
            "OPERATIONS_MANAGER",
        )
    }
}
