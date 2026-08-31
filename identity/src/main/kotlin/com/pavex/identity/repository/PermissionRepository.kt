package com.pavex.identity.repository

import com.pavex.identity.entity.Permission
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PermissionRepository : JpaRepository<Permission, UUID> {
    fun findByCode(code: String): Permission?

    fun findAllByIdIn(ids: Collection<UUID>): List<Permission>

    fun findAllByCodeIn(codes: Collection<String>): List<Permission>

    fun findAllByOrderByCodeAsc(): List<Permission>
}
