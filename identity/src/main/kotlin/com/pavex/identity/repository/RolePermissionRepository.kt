package com.pavex.identity.repository

import com.pavex.identity.entity.RolePermission
import com.pavex.identity.entity.RolePermissionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RolePermissionRepository : JpaRepository<RolePermission, RolePermissionId> {
    fun findAllByRoleIdIn(roleIds: Collection<UUID>): List<RolePermission>

    fun findAllByRoleId(roleId: UUID): List<RolePermission>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RolePermission rp where rp.roleId = :roleId")
    fun deleteAllByRoleId(
        @Param("roleId") roleId: UUID,
    ): Int
}
