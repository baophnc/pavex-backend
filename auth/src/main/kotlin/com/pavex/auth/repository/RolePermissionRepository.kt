package com.pavex.auth.repository

import com.pavex.auth.entity.RolePermission
import com.pavex.auth.entity.RolePermissionId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RolePermissionRepository :
    JpaRepository<RolePermission, RolePermissionId> {

    fun findAllByRoleIdIn(
        roleIds: Collection<UUID>,
    ): List<RolePermission>
}
