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
import com.pavex.identity.dto.role.PermissionResponse
import com.pavex.identity.dto.role.RoleResponse
import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.exception.CannotRemoveOwnAdminRoleException
import com.pavex.identity.exception.LastActiveAdminException
import com.pavex.identity.exception.RoleNotFoundException
import com.pavex.identity.exception.UserNotFoundException
import com.pavex.identity.repository.UserAccountRepository
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
    private val userAccountRepository:
    UserAccountRepository,
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


    @Transactional
    fun bootstrapRoles(
        userId: UUID,
        rawRoleCodes: Set<String>,
        now: Instant,
    ) {
        val user = userAccountRepository
            .findById(userId)
            .orElseThrow { UserNotFoundException() }

        val roleCodes = rawRoleCodes
            .map { normalizeRoleCode(it) }
            .filter { it.isNotBlank() }
            .toSet()

        if (roleCodes.isEmpty()) {
            assignDefaultCustomerRole(userId, now)
            return
        }

        val desiredRoles = roleCodes.associateWith { code ->
            roleRepository.findByCode(code)
                ?: throw RoleNotFoundException()
        }

        val existingAssignments = userRoleRepository
            .findAllByUserId(userId)

        val existingRoleIds = existingAssignments
            .map { it.roleId }
            .toSet()

        val desiredRoleIds = desiredRoles
            .values
            .map { it.id }
            .toSet()

        var changed = false

        existingAssignments
            .filter { it.roleId !in desiredRoleIds }
            .forEach { assignment ->
                userRoleRepository.delete(assignment)
                changed = true
            }

        desiredRoles.values
            .filter { it.id !in existingRoleIds }
            .forEach { role ->
                userRoleRepository.save(
                    UserRole(
                        userId = userId,
                        roleId = role.id,
                        assignedAt = now,
                    )
                )
                changed = true
            }

        if (changed) {
            user.authorizationVersion += 1
            user.updatedAt = now
            userAccountRepository.save(user)
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
    @Transactional(
        readOnly = true,
    )
    fun getRoles():
            List<RoleResponse> {

        val roles =
            roleRepository
                .findAllByOrderByCodeAsc()

        if (roles.isEmpty()) {
            return emptyList()
        }

        val roleIds =
            roles
                .map { it.id }

        val mappings =
            rolePermissionRepository
                .findAllByRoleIdIn(
                    roleIds
                )

        val permissionIds =
            mappings
                .map { it.permissionId }
                .toSet()

        val permissions =
            if (permissionIds.isEmpty()) {
                emptyMap()
            } else {
                permissionRepository
                    .findAllByIdIn(
                        permissionIds
                    )
                    .associateBy {
                        it.id
                    }
            }

        val permissionsByRole =
            mappings
                .groupBy {
                    it.roleId
                }

        return roles.map { role ->

            val rolePermissions =
                permissionsByRole[
                    role.id
                ]
                    .orEmpty()
                    .mapNotNull {
                        permissions[
                            it.permissionId
                        ]
                    }
                    .sortedBy {
                        it.code
                    }
                    .map {
                        PermissionResponse(
                            id = it.id,
                            code = it.code,
                            name = it.name,
                            description =
                                it.description,
                        )
                    }

            RoleResponse(
                id = role.id,
                code = role.code,
                name = role.name,
                description =
                    role.description,
                isSystem =
                    role.isSystem,
                permissions =
                    rolePermissions,
            )
        }
    }
    @Transactional
    fun assignRole(
        actorUserId: UUID,
        targetUserId: UUID,
        rawRoleCode: String,
    ) {
        val roleCode =
            normalizeRoleCode(
                rawRoleCode
            )

        val user =
            userAccountRepository
                .findById(
                    targetUserId
                )
                .orElseThrow {
                    UserNotFoundException()
                }

        val role =
            roleRepository
                .findByCode(
                    roleCode
                )
                ?: throw RoleNotFoundException()

        val existing =
            userRoleRepository
                .findByUserIdAndRoleId(
                    userId =
                        user.id,

                    roleId =
                        role.id,
                )

        /*
         * Idempotent:
         * assign role đã tồn tại -> success.
         */
        if (existing != null) {
            return
        }

        userRoleRepository.save(
            UserRole(
                userId =
                    user.id,

                roleId =
                    role.id,

                assignedAt =
                    Instant.now(),

                assignedBy =
                    actorUserId,
            )
        )
        user.authorizationVersion += 1
        user.updatedAt = Instant.now()
        userAccountRepository.save(user)
    }
    @Transactional
    fun removeRole(
        actorUserId: UUID,
        targetUserId: UUID,
        rawRoleCode: String,
    ) {
        val roleCode =
            normalizeRoleCode(
                rawRoleCode
            )

        if (
            roleCode == ADMIN_ROLE &&
            actorUserId == targetUserId
        ) {
            throw CannotRemoveOwnAdminRoleException()
        }

        /*
         * Riêng ADMIN lấy pessimistic lock
         * trên role row.
         *
         * Hai request remove ADMIN song song
         * không thể cùng kiểm tra count rồi xóa.
         */
        val role =
            if (roleCode == ADMIN_ROLE) {

                roleRepository
                    .findByCodeForUpdate(
                        ADMIN_ROLE
                    )

            } else {

                roleRepository
                    .findByCode(
                        roleCode
                    )
            }
                ?: throw RoleNotFoundException()

        val user =
            userAccountRepository
                .findById(
                    targetUserId
                )
                .orElseThrow {
                    UserNotFoundException()
                }

        val userRole =
            userRoleRepository
                .findByUserIdAndRoleId(
                    userId =
                        user.id,

                    roleId =
                        role.id,
                )
                ?: throw RoleNotFoundException()

        if (
            roleCode == ADMIN_ROLE &&
            user.status == AccountStatus.ACTIVE
        ) {

            val activeAdmins =
                userRoleRepository
                    .countActiveUsersWithRoleCode(
                        ADMIN_ROLE
                    )

            if (activeAdmins <= 1) {
                throw LastActiveAdminException()
            }
        }

        userRoleRepository.delete(
            userRole
        )
        user.authorizationVersion += 1
        user.updatedAt = Instant.now()
        userAccountRepository.save(user)
    }

    /**
     * Narrow, idempotent operation used by Partners' durable outbox. A retry
     * after a lost HTTP response must not fail merely because the desired role
     * state was already committed.
     */
    @Transactional
    fun synchronizeMerchantRole(
        actorUserId: UUID,
        targetUserId: UUID,
        enabled: Boolean,
    ) {
        if (enabled) {
            assignRole(actorUserId, targetUserId, MERCHANT_ROLE)
            return
        }

        val role = roleRepository.findByCode(MERCHANT_ROLE)
            ?: throw RoleNotFoundException()
        if (userRoleRepository.findByUserIdAndRoleId(targetUserId, role.id) == null) {
            return
        }
        removeRole(actorUserId, targetUserId, MERCHANT_ROLE)
    }


    companion object {

        const val CUSTOMER_ROLE =
            "CUSTOMER"

        const val ADMIN_ROLE =
            "ADMIN"

        const val MERCHANT_ROLE =
            "MERCHANT"
    }
    private fun normalizeRoleCode(
        value: String,
    ): String {

        return value
            .trim()
            .uppercase()
    }
}


data class UserAuthorization(
    val roles: Set<String>,
    val permissions: Set<String>,
)
