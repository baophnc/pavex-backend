package com.pavex.identity.service

import com.pavex.identity.dto.role.CreateRoleRequest
import com.pavex.identity.dto.role.PermissionResponse
import com.pavex.identity.dto.role.ReplaceRolePermissionsRequest
import com.pavex.identity.dto.role.RoleResponse
import com.pavex.identity.dto.role.UpdateRoleRequest
import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.Permission
import com.pavex.identity.entity.Role
import com.pavex.identity.entity.RolePermission
import com.pavex.identity.entity.UserRole
import com.pavex.identity.entity.UserRoleId
import com.pavex.identity.exception.CannotRemoveOwnAdminRoleException
import com.pavex.identity.exception.InvalidRoleCodeException
import com.pavex.identity.exception.InvalidRoleNameException
import com.pavex.identity.exception.LastActiveAdminException
import com.pavex.identity.exception.RoleAlreadyExistsException
import com.pavex.identity.exception.RoleInUseException
import com.pavex.identity.exception.RoleNotFoundException
import com.pavex.identity.exception.SystemRoleProtectedException
import com.pavex.identity.exception.UnknownPermissionException
import com.pavex.identity.exception.UserNotFoundException
import com.pavex.identity.exception.WorkforceRoleManagedByOperationsException
import com.pavex.identity.repository.PermissionRepository
import com.pavex.identity.repository.RolePermissionRepository
import com.pavex.identity.repository.RoleRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserRoleRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RoleService(
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val permissionRepository: PermissionRepository,
    private val userAccountRepository: UserAccountRepository,
) {

    @Transactional
    fun assignDefaultCustomerRole(
        userId: UUID,
        now: Instant,
    ) {
        val role = roleRepository.findByCode(CUSTOMER_ROLE)
            ?: error("Default CUSTOMER role has not been seeded")

        val id = UserRoleId(
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

        val existingAssignments = userRoleRepository.findAllByUserId(userId)
        val existingRoleIds = existingAssignments.map { it.roleId }.toSet()
        val desiredRoleIds = desiredRoles.values.map { it.id }.toSet()
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
    fun getAuthorization(userId: UUID): UserAuthorization {
        val userRoles = userRoleRepository.findAllByUserId(userId)
        if (userRoles.isEmpty()) {
            return UserAuthorization(
                roles = emptySet(),
                permissions = emptySet(),
            )
        }

        val roleIds = userRoles.map { it.roleId }.toSet()
        val roles = roleRepository.findAllByIdIn(roleIds)
        val mappings = rolePermissionRepository.findAllByRoleIdIn(roleIds)
        val permissionIds = mappings.map { it.permissionId }.toSet()
        val permissions = if (permissionIds.isEmpty()) {
            emptyList()
        } else {
            permissionRepository.findAllByIdIn(permissionIds)
        }

        return UserAuthorization(
            roles = roles.map { it.code }.toSet(),
            permissions = permissions.map { it.code }.toSet(),
        )
    }

    @Transactional(readOnly = true)
    fun getRoles(): List<RoleResponse> {
        val roles = roleRepository.findAllByOrderByCodeAsc()
        if (roles.isEmpty()) return emptyList()

        val mappings = rolePermissionRepository.findAllByRoleIdIn(roles.map { it.id })
        val permissionIds = mappings.map { it.permissionId }.toSet()
        val permissionsById = if (permissionIds.isEmpty()) {
            emptyMap()
        } else {
            permissionRepository.findAllByIdIn(permissionIds).associateBy { it.id }
        }
        val mappingsByRoleId = mappings.groupBy { it.roleId }

        return roles.map { role ->
            val permissions = mappingsByRoleId[role.id]
                .orEmpty()
                .mapNotNull { permissionsById[it.permissionId] }
                .sortedBy { it.code }

            role.toResponse(permissions)
        }
    }

    @Transactional(readOnly = true)
    fun getPermissions(): List<PermissionResponse> =
        permissionRepository
            .findAllByOrderByCodeAsc()
            .map { it.toResponse() }

    @Transactional
    fun createRole(request: CreateRoleRequest): RoleResponse {
        val code = normalizeRoleCode(request.code)
        validateRoleCode(code)
        val name = normalizeRoleName(request.name)
        val description = normalizeDescription(request.description)

        if (roleRepository.findByCode(code) != null) {
            throw RoleAlreadyExistsException(code)
        }

        val permissions = resolvePermissions(request.permissionCodes)
        val role = Role(
            code = code,
            name = name,
            description = description,
            isSystem = false,
        )

        try {
            roleRepository.saveAndFlush(role)
        } catch (_: DataIntegrityViolationException) {
            throw RoleAlreadyExistsException(code)
        }

        if (permissions.isNotEmpty()) {
            rolePermissionRepository.saveAll(
                permissions.map { permission ->
                    RolePermission(
                        roleId = role.id,
                        permissionId = permission.id,
                    )
                }
            )
        }

        return role.toResponse(permissions)
    }

    @Transactional
    fun updateRole(
        roleId: UUID,
        request: UpdateRoleRequest,
    ): RoleResponse {
        val role = getMutableRoleForUpdate(roleId)
        role.name = normalizeRoleName(request.name)
        role.description = normalizeDescription(request.description)
        roleRepository.save(role)

        return role.toResponse(loadPermissions(role.id))
    }

    @Transactional
    fun replacePermissions(
        roleId: UUID,
        request: ReplaceRolePermissionsRequest,
    ): RoleResponse {
        val role = getMutableRoleForUpdate(roleId)
        val desiredPermissions = resolvePermissions(request.permissionCodes)
        val desiredIds = desiredPermissions.map { it.id }.toSet()
        val currentMappings = rolePermissionRepository.findAllByRoleId(role.id)
        val currentIds = currentMappings.map { it.permissionId }.toSet()

        if (currentIds == desiredIds) {
            return role.toResponse(desiredPermissions)
        }

        rolePermissionRepository.deleteAllByRoleId(role.id)
        if (desiredPermissions.isNotEmpty()) {
            rolePermissionRepository.saveAll(
                desiredPermissions.map { permission ->
                    RolePermission(
                        roleId = role.id,
                        permissionId = permission.id,
                    )
                }
            )
        }

        userAccountRepository.bumpAuthorizationVersionForRole(
            roleId = role.id,
            updatedAt = Instant.now(),
        )

        return role.toResponse(desiredPermissions)
    }

    @Transactional
    fun deleteRole(roleId: UUID) {
        val role = getMutableRoleForUpdate(roleId)
        if (userRoleRepository.existsByRoleId(role.id)) {
            throw RoleInUseException()
        }

        rolePermissionRepository.deleteAllByRoleId(role.id)
        roleRepository.delete(role)
    }

    @Transactional
    fun assignRole(
        actorUserId: UUID,
        targetUserId: UUID,
        rawRoleCode: String,
    ) {
        val roleCode = normalizeRoleCode(rawRoleCode)
        if (roleCode in WORKFORCE_MANAGED_ROLES) throw WorkforceRoleManagedByOperationsException()
        val user = userAccountRepository
            .findById(targetUserId)
            .orElseThrow { UserNotFoundException() }
        val role = roleRepository.findByCode(roleCode)
            ?: throw RoleNotFoundException()
        val existing = userRoleRepository.findByUserIdAndRoleId(
            userId = user.id,
            roleId = role.id,
        )

        // Idempotent: assigning an existing role is already the desired state.
        if (existing != null) return

        val now = Instant.now()
        userRoleRepository.save(
            UserRole(
                userId = user.id,
                roleId = role.id,
                assignedAt = now,
                assignedBy = actorUserId,
            )
        )
        user.authorizationVersion += 1
        user.updatedAt = now
        userAccountRepository.save(user)
    }

    @Transactional
    fun removeRole(
        actorUserId: UUID,
        targetUserId: UUID,
        rawRoleCode: String,
    ) {
        val roleCode = normalizeRoleCode(rawRoleCode)
        if (roleCode in WORKFORCE_MANAGED_ROLES) throw WorkforceRoleManagedByOperationsException()
        if (roleCode == ADMIN_ROLE && actorUserId == targetUserId) {
            throw CannotRemoveOwnAdminRoleException()
        }

        // ADMIN removal is serialized so two concurrent requests cannot both
        // observe an active-admin count greater than zero and remove the last admin.
        val role = if (roleCode == ADMIN_ROLE) {
            roleRepository.findByCodeForUpdate(ADMIN_ROLE)
        } else {
            roleRepository.findByCode(roleCode)
        } ?: throw RoleNotFoundException()

        val user = userAccountRepository
            .findById(targetUserId)
            .orElseThrow { UserNotFoundException() }
        val userRole = userRoleRepository.findByUserIdAndRoleId(
            userId = user.id,
            roleId = role.id,
        ) ?: throw RoleNotFoundException()

        if (roleCode == ADMIN_ROLE && user.status == AccountStatus.ACTIVE) {
            val activeAdmins = userRoleRepository.countActiveUsersWithRoleCode(ADMIN_ROLE)
            if (activeAdmins <= 1) {
                throw LastActiveAdminException()
            }
        }

        val now = Instant.now()
        userRoleRepository.delete(userRole)
        user.authorizationVersion += 1
        user.updatedAt = now
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

    private fun getMutableRoleForUpdate(roleId: UUID): Role {
        val role = roleRepository.findByIdForUpdate(roleId)
            ?: throw RoleNotFoundException()
        if (role.isSystem) {
            throw SystemRoleProtectedException()
        }
        return role
    }

    private fun resolvePermissions(rawPermissionCodes: Collection<String>): List<Permission> {
        if (rawPermissionCodes.isEmpty()) return emptyList()

        val permissionCodes = rawPermissionCodes.map { it.trim() }.toSet()
        val permissions = permissionRepository.findAllByCodeIn(permissionCodes)
        val foundCodes = permissions.map { it.code }.toSet()
        val missingCodes = permissionCodes - foundCodes
        if (missingCodes.isNotEmpty()) {
            throw UnknownPermissionException(missingCodes)
        }

        return permissions.sortedBy { it.code }
    }

    private fun loadPermissions(roleId: UUID): List<Permission> {
        val mappings = rolePermissionRepository.findAllByRoleId(roleId)
        if (mappings.isEmpty()) return emptyList()
        return permissionRepository
            .findAllByIdIn(mappings.map { it.permissionId }.toSet())
            .sortedBy { it.code }
    }

    private fun Role.toResponse(permissions: List<Permission>): RoleResponse =
        RoleResponse(
            id = id,
            code = code,
            name = name,
            description = description,
            isSystem = isSystem,
            permissions = permissions.map { it.toResponse() },
        )

    private fun Permission.toResponse(): PermissionResponse =
        PermissionResponse(
            id = id,
            code = code,
            name = name,
            description = description,
        )

    private fun normalizeRoleCode(value: String): String =
        value.trim().uppercase()

    private fun normalizeRoleName(value: String): String {
        val name = value.trim()
        if (name.length !in 2..128) {
            throw InvalidRoleNameException()
        }
        return name
    }

    private fun normalizeDescription(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun validateRoleCode(code: String) {
        if (!ROLE_CODE_REGEX.matches(code)) {
            throw InvalidRoleCodeException()
        }
    }

    companion object {
        const val CUSTOMER_ROLE = "CUSTOMER"
        const val ADMIN_ROLE = "ADMIN"
        const val MERCHANT_ROLE = "MERCHANT"
        val WORKFORCE_MANAGED_ROLES = setOf("DRIVER", "WAREHOUSE_STAFF", "HUB_MANAGER", "OPERATIONS_MANAGER")

        private val ROLE_CODE_REGEX = Regex("^[A-Z][A-Z0-9_]{2,63}$")
    }
}

data class UserAuthorization(
    val roles: Set<String>,
    val permissions: Set<String>,
)
