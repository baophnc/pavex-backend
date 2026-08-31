package com.pavex.identity.service

import com.pavex.contracts.operations.WorkforceIdentitySnapshot
import com.pavex.contracts.operations.WorkforceIdentitySnapshotRequest
import com.pavex.identity.repository.RoleRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import com.pavex.identity.repository.UserRoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkforceIdentityLookupService(
    private val accounts: UserAccountRepository,
    private val profiles: UserProfileRepository,
    private val userRoles: UserRoleRepository,
    private val roles: RoleRepository,
) {
    @Transactional(readOnly = true)
    fun snapshots(request: WorkforceIdentitySnapshotRequest): List<WorkforceIdentitySnapshot> {
        val requestedIds = request.userIds
            .asSequence()
            .filter { it != ZERO_UUID }
            .distinct()
            .take(MAX_BATCH_SIZE)
            .toList()
        if (requestedIds.isEmpty()) return emptyList()

        val accountRows = accounts.findAllById(requestedIds)
        if (accountRows.isEmpty()) return emptyList()

        val ids = accountRows.map { it.id }
        val profileByUser = profiles.findAllByUserIdIn(ids).associateBy { it.userId }
        val mappings = userRoles.findAllByUserIdIn(ids)
        val roleIds = mappings.map { it.roleId }.toSet()
        val rolesById = if (roleIds.isEmpty()) emptyMap() else roles.findAllByIdIn(roleIds).associateBy { it.id }
        val roleCodesByUser = mappings
            .groupBy { it.userId }
            .mapValues { (_, values) ->
                values.mapNotNull { rolesById[it.roleId]?.code }.distinct().sorted()
            }
        val needle = request.search?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        return accountRows
            .asSequence()
            .map { account ->
                val profile = profileByUser[account.id]
                WorkforceIdentitySnapshot(
                    userId = account.id,
                    email = account.email,
                    emailVerified = account.emailVerified,
                    accountStatus = account.status.name,
                    profileStatus = account.profileStatus.name,
                    firstName = profile?.firstName,
                    lastName = profile?.lastName,
                    displayName = profile?.displayName,
                    gender = profile?.gender?.name,
                    dateOfBirth = profile?.dateOfBirth,
                    avatarUrl = profile?.avatarUrl,
                    roles = roleCodesByUser[account.id].orEmpty(),
                    createdAt = account.createdAt,
                    updatedAt = account.updatedAt,
                )
            }
            .filter { snapshot ->
                needle == null || listOf(
                    snapshot.email,
                    snapshot.firstName,
                    snapshot.lastName,
                    snapshot.displayName,
                ).any { value -> value?.lowercase()?.contains(needle) == true }
            }
            .sortedBy { it.email.lowercase() }
            .toList()
    }

    companion object {
        private val ZERO_UUID: UUID = UUID(0, 0)
        private const val MAX_BATCH_SIZE = 1000
    }
}
