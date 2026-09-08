package com.pavex.identity.service

import com.pavex.identity.dto.role.CreateRoleRequest
import com.pavex.identity.dto.role.ReplaceRolePermissionsRequest
import com.pavex.identity.dto.role.UpdateRoleRequest
import com.pavex.identity.entity.Permission
import com.pavex.identity.entity.Role
import com.pavex.identity.entity.RolePermission
import com.pavex.identity.exception.RoleInUseException
import com.pavex.identity.exception.SystemRoleProtectedException
import com.pavex.identity.exception.WorkforceRoleManagedByOperationsException
import com.pavex.identity.repository.PermissionRepository
import com.pavex.identity.repository.RolePermissionRepository
import com.pavex.identity.repository.RoleRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserRoleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID

class RoleServiceTest {

    @Test
    fun `creates custom role with backend-owned permissions`() {
        val permission = permission("partners.merchants.read")
        val mappings = mutableListOf<RolePermission>()

        val service = service(
            role = null,
            permissionsByCode = listOf(permission),
            onSaveMappings = { mappings += it },
        )

        val result = service.createRole(
            CreateRoleRequest(
                code = "MERCHANT_REVIEWER",
                name = "Merchant Reviewer",
                description = "Review merchant applications",
                permissionCodes = setOf(permission.code),
            )
        )

        assertEquals("MERCHANT_REVIEWER", result.code)
        assertFalse(result.isSystem)
        assertEquals(listOf(permission.code), result.permissions.map { it.code })
        assertEquals(1, mappings.size)
    }

    @Test
    fun `system roles cannot be edited`() {
        val systemRole = Role(
            code = "ADMIN",
            name = "Administrator",
            isSystem = true,
        )
        val service = service(role = systemRole)

        assertThrows(SystemRoleProtectedException::class.java) {
            service.updateRole(
                systemRole.id,
                UpdateRoleRequest(
                    name = "Changed",
                    description = null,
                ),
            )
        }
    }

    @Test
    fun `replacing permission matrix invalidates assigned users authorization version`() {
        val role = Role(
            code = "MERCHANT_REVIEWER",
            name = "Merchant Reviewer",
            isSystem = false,
        )
        val oldPermission = permission("partners.merchants.read")
        val newPermission = permission("partners.merchants.manage-status")
        var deletedRoleId: UUID? = null
        var bumpedRoleId: UUID? = null
        var savedMappings = emptyList<RolePermission>()

        val service = service(
            role = role,
            permissionsByCode = listOf(newPermission),
            currentMappings = listOf(
                RolePermission(
                    roleId = role.id,
                    permissionId = oldPermission.id,
                )
            ),
            onDeleteMappings = { deletedRoleId = it },
            onSaveMappings = { savedMappings = it },
            onBumpAuthorization = { bumpedRoleId = it },
        )

        val result = service.replacePermissions(
            role.id,
            ReplaceRolePermissionsRequest(
                permissionCodes = setOf(newPermission.code),
            ),
        )

        assertEquals(role.id, deletedRoleId)
        assertEquals(role.id, bumpedRoleId)
        assertEquals(listOf(newPermission.id), savedMappings.map { it.permissionId })
        assertEquals(listOf(newPermission.code), result.permissions.map { it.code })
    }

    @Test
    fun `bootstrap synchronization grants every catalog permission to admin`() {
        val adminRole = Role(
            code = "ADMIN",
            name = "Administrator",
            isSystem = true,
        )
        val existingPermission = permission("operations.workforce.read")
        val newPermission = permission("operations.workforce.manage")
        var bumpedRoleId: UUID? = null
        var savedMappings = emptyList<RolePermission>()

        val service = service(
            role = adminRole,
            permissionsByCode = listOf(existingPermission, newPermission),
            currentMappings = listOf(
                RolePermission(
                    roleId = adminRole.id,
                    permissionId = existingPermission.id,
                )
            ),
            onSaveMappings = { savedMappings = it },
            onBumpAuthorization = { bumpedRoleId = it },
        )

        val added = service.synchronizeSystemAdminPermissions(
            Instant.parse("2026-09-03T00:00:00Z")
        )

        assertEquals(1, added)
        assertEquals(listOf(newPermission.id), savedMappings.map { it.permissionId })
        assertEquals(adminRole.id, bumpedRoleId)
    }

    @Test
    fun `workforce roles cannot be assigned directly from identity`() {
        val service = service(role = null)

        assertThrows(WorkforceRoleManagedByOperationsException::class.java) {
            service.assignRole(UUID.randomUUID(), UUID.randomUUID(), "DRIVER")
        }
    }

    @Test
    fun `workforce roles cannot be removed directly from identity`() {
        val service = service(role = null)

        assertThrows(WorkforceRoleManagedByOperationsException::class.java) {
            service.removeRole(UUID.randomUUID(), UUID.randomUUID(), "HUB_MANAGER")
        }
    }

    @Test
    fun `assigned custom role cannot be deleted`() {
        val role = Role(
            code = "SUPPORT_AGENT",
            name = "Support Agent",
            isSystem = false,
        )
        val service = service(
            role = role,
            roleInUse = true,
        )

        assertThrows(RoleInUseException::class.java) {
            service.deleteRole(role.id)
        }
    }

    private fun service(
        role: Role?,
        permissionsByCode: List<Permission> = emptyList(),
        currentMappings: List<RolePermission> = emptyList(),
        roleInUse: Boolean = false,
        onSaveMappings: (List<RolePermission>) -> Unit = {},
        onDeleteMappings: (UUID) -> Unit = {},
        onBumpAuthorization: (UUID) -> Unit = {},
    ): RoleService {
        val roleRepository = proxy<RoleRepository> { method, args ->
            when (method) {
                "findByCode" -> role?.takeIf { it.code == args?.firstOrNull() }
                "findByCodeForUpdate" -> role?.takeIf { it.code == args?.firstOrNull() }
                "findByIdForUpdate" -> role?.takeIf { it.id == args?.firstOrNull() }
                "save", "saveAndFlush" -> args?.first()
                "delete" -> Unit
                else -> defaultValue(method)
            }
        }
        val permissionRepository = proxy<PermissionRepository> { method, _ ->
            when (method) {
                "findAllByCodeIn" -> permissionsByCode
                "findAllByIdIn" -> permissionsByCode
                "findAllByOrderByCodeAsc" -> permissionsByCode.sortedBy { it.code }
                else -> defaultValue(method)
            }
        }
        val rolePermissionRepository = proxy<RolePermissionRepository> { method, args ->
            when (method) {
                "findAllByRoleId" -> currentMappings
                "saveAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    val values = (args?.first() as Iterable<RolePermission>).toList()
                    onSaveMappings(values)
                    values
                }
                "deleteAllByRoleId" -> {
                    onDeleteMappings(args?.first() as UUID)
                    currentMappings.size
                }
                else -> defaultValue(method)
            }
        }
        val userRoleRepository = proxy<UserRoleRepository> { method, _ ->
            when (method) {
                "existsByRoleId" -> roleInUse
                else -> defaultValue(method)
            }
        }
        val userAccountRepository = proxy<UserAccountRepository> { method, args ->
            when (method) {
                "bumpAuthorizationVersionForRole" -> {
                    onBumpAuthorization(args?.first() as UUID)
                    1
                }
                else -> defaultValue(method)
            }
        }

        return RoleService(
            roleRepository = roleRepository,
            userRoleRepository = userRoleRepository,
            rolePermissionRepository = rolePermissionRepository,
            permissionRepository = permissionRepository,
            userAccountRepository = userAccountRepository,
        )
    }

    private fun permission(code: String): Permission =
        Permission(
            code = code,
            name = code,
            description = null,
        )

    private inline fun <reified T> proxy(
        crossinline handler: (String, Array<out Any?>?) -> Any?,
    ): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { instance, method, args ->
        when (method.name) {
            "toString" -> "${T::class.simpleName}TestProxy"
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === args?.firstOrNull()
            else -> handler(method.name, args)
        }
    } as T

    private fun defaultValue(method: String): Any? = when {
        method.startsWith("exists") -> false
        method.startsWith("count") -> 0L
        method.startsWith("findAll") -> emptyList<Any>()
        method.startsWith("deleteAll") -> 0
        else -> null
    }
}
