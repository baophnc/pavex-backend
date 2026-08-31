package com.pavex.identity.repository

import com.pavex.identity.entity.UserRole
import com.pavex.identity.entity.UserRoleId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
interface UserRoleRepository :
    JpaRepository<UserRole, UserRoleId> {

    fun findAllByUserId(
        userId: UUID,
    ): List<UserRole>

    fun findByUserIdAndRoleId(
        userId: UUID,
        roleId: UUID,
    ): UserRole?
    fun findAllByUserIdIn(
        userIds: Collection<UUID>,
    ): List<UserRole>

    fun existsByUserIdAndRoleId(
        userId: UUID,
        roleId: UUID,
    ): Boolean

    fun existsByRoleId(roleId: UUID): Boolean

    @Query(
        value = """
        SELECT COUNT(*)
        FROM identity.user_role ur

        JOIN identity.role r
          ON r.id = ur.role_id

        JOIN identity.user_account u
          ON u.id = ur.user_id

        WHERE r.code = :roleCode
          AND u.status = 'ACTIVE'
    """,
        nativeQuery = true,
    )
    fun countActiveUsersWithRoleCode(
        @Param("roleCode")
        roleCode: String,
    ): Long
}
