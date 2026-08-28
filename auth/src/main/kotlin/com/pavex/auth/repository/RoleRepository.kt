package com.pavex.auth.repository

import com.pavex.auth.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
interface RoleRepository :
    JpaRepository<Role, UUID> {

    fun findByCode(
        code: String,
    ): Role?

    fun findAllByIdIn(
        ids: Collection<UUID>,
    ): List<Role>
    fun findAllByOrderByCodeAsc():
            List<Role>


    @Lock(
        LockModeType.PESSIMISTIC_WRITE
    )
    @Query(
        """
    select r
    from Role r
    where r.code = :code
    """
    )
    fun findByCodeForUpdate(
        @Param("code")
        code: String,
    ): Role?
}
