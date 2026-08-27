package com.pavex.identity.service

import com.pavex.identity.entity.UserRole
import com.pavex.identity.repository.PermissionRepository
import com.pavex.identity.repository.RolePermissionRepository
import com.pavex.identity.repository.RoleRepository
import com.pavex.identity.repository.UserRoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RoleService(

    private val roleRepository:
        RoleRepository,

    private val userRoleRepository:
        UserRoleRepository,

    private val rolePermissionRepository:
        RolePermissionRepository,

    private val permissionRepository:
        PermissionRepository,
) {

    @Transactional
    fun assignDefaultCustomerRole(
        userId: UUID,
        now: Instant,
    ) {

        val role = roleRepository
            .findByCode(CUSTOMER_ROLE)
            ?: error(
                "Default CUSTOMER role has not been seeded"
            )

        val id = com.pavex.identity.entity.UserRoleId(
            userId = userId,
            roleId = role.id,
        )

        if (!userRoleRepository.existsById(id)) {

            userRoleRepository.save(
                UserRole(
                    userId = userId,
                    roleId = role.id,
                    assignedAt = now,
                )
            )
        }
    }


    @Transactional(readOnly = true)
    fun getAuthorization(
        userId: UUID,
    ): UserAuthorization {

        val userRoles =
            userRoleRepository
                .findAllByUserId(userId)

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
            roleRepository
                .findAllByIdIn(roleIds)

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


    companion object {
        const val CUSTOMER_ROLE =
            "CUSTOMER"
    }
}


data class UserAuthorization(
    val roles: Set<String>,
    val permissions: Set<String>,
)
