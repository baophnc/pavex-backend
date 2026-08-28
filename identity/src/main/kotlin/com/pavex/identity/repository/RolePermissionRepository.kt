package com.pavex.identity.repository

import com.pavex.identity.entity.RolePermission
import com.pavex.identity.entity.RolePermissionId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RolePermissionRepository :
    JpaRepository<RolePermission, RolePermissionId> {

    fun findAllByRoleIdIn(
        roleIds: Collection<UUID>,
    ): List<RolePermission>
}
