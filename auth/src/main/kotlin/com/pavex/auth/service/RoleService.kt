package com.pavex.auth.service

import com.pavex.auth.repository.PermissionRepository
import com.pavex.auth.repository.RolePermissionRepository
import com.pavex.auth.repository.RoleRepository
import com.pavex.auth.repository.UserRoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Transitional read-only adapter for legacy Auth RBAC data.
 *
 * New role/profile/user writes belong to Identity. This service exists only so
 * JwtService can preserve existing role assignments while a legacy Auth user is
 * provisioned into Identity for the first time. Delete it together with the old
 * Auth RBAC tables after the cutover migration has completed.
 */
@Service
class RoleService(
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val permissionRepository: PermissionRepository,
) {

    @Transactional(readOnly = true)
    fun getAuthorization(
        userId: UUID,
    ): UserAuthorization {
        val userRoles =
            userRoleRepository.findAllByUserId(userId)

        if (userRoles.isEmpty()) {
            return UserAuthorization(
                roles = emptySet(),
                permissions = emptySet(),
            )
        }

        val roleIds =
            userRoles
                .map { it.roleId }
                .toSet()

        val roles =
            roleRepository.findAllByIdIn(roleIds)

        val rolePermissions =
            rolePermissionRepository
                .findAllByRoleIdIn(roleIds)

        val permissionIds =
            rolePermissions
                .map { it.permissionId }
                .toSet()

        val permissions =
            if (permissionIds.isEmpty()) {
                emptyList()
            } else {
                permissionRepository
                    .findAllByIdIn(permissionIds)
            }

        return UserAuthorization(
            roles = roles
                .map { it.code }
                .toSet(),
            permissions = permissions
                .map { it.code }
                .toSet(),
        )
    }
}

data class UserAuthorization(
    val roles: Set<String>,
    val permissions: Set<String>,
)
