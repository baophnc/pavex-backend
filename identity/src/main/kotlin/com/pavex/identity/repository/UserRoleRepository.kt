package com.pavex.identity.repository

import com.pavex.identity.entity.UserRole
import com.pavex.identity.entity.UserRoleId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRoleRepository :
    JpaRepository<UserRole, UserRoleId> {

    fun findAllByUserId(
        userId: UUID,
    ): List<UserRole>
}
