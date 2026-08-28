package com.pavex.network.repository

import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.HubType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface HubRepository : JpaRepository<Hub, UUID> {
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun findAllByParentHubId(parentHubId: UUID): List<Hub>

    @Query(
        """
        SELECT h FROM Hub h
        WHERE (:status IS NULL OR h.status = :status)
          AND (:type IS NULL OR h.type = :type)
          AND (:provinceCode = '' OR h.provinceCode = :provinceCode)
          AND (:search = '' OR
               LOWER(h.code) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(h.name) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
    )
    fun searchAdmin(
        @Param("status") status: HubStatus?,
        @Param("type") type: HubType?,
        @Param("provinceCode") provinceCode: String,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<Hub>

    @Query(
        """
        SELECT h FROM Hub h
        WHERE h.status = :activeStatus
          AND h.publicVisible = true
          AND (:provinceCode = '' OR h.provinceCode = :provinceCode)
          AND (:search = '' OR
               LOWER(h.code) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(h.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(COALESCE(h.publicName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
    )
    fun searchPublic(
        @Param("activeStatus") activeStatus: HubStatus,
        @Param("provinceCode") provinceCode: String,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<Hub>
}
