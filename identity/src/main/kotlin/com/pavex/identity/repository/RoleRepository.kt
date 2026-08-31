package com.pavex.identity.repository

import com.pavex.identity.entity.Role
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findByCode(code: String): Role?

    fun findAllByIdIn(ids: Collection<UUID>): List<Role>

    fun findAllByOrderByCodeAsc(): List<Role>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.code = :code")
    fun findByCodeForUpdate(
        @Param("code") code: String,
    ): Role?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): Role?
}
