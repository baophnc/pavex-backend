package com.pavex.identity.repository

import com.pavex.identity.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoleRepository :
    JpaRepository<Role, UUID> {

    fun findByCode(
        code: String,
    ): Role?

    fun findAllByIdIn(
        ids: Collection<UUID>,
    ): List<Role>
}
