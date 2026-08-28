package com.pavex.auth.repository

import com.pavex.auth.entity.Permission
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PermissionRepository :
    JpaRepository<Permission, UUID> {

    fun findByCode(
        code: String,
    ): Permission?

    fun findAllByIdIn(
        ids: Collection<UUID>,
    ): List<Permission>
}
