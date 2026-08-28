package com.pavex.network.repository

import com.pavex.network.entity.ServiceAreaCoverage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ServiceAreaCoverageRepository : JpaRepository<ServiceAreaCoverage, UUID> {
    fun findFirstByWardCodeAndIsActiveTrue(wardCode: String): ServiceAreaCoverage?
    fun findFirstByProvinceCodeAndWardCodeIsNullAndIsActiveTrue(provinceCode: String): ServiceAreaCoverage?
    fun existsByServiceAreaIdAndIsActiveTrue(serviceAreaId: UUID): Boolean

    @Query(
        """
        SELECT c FROM ServiceAreaCoverage c
        WHERE (:serviceAreaId IS NULL OR c.serviceAreaId = :serviceAreaId)
          AND (:provinceCode = '' OR c.provinceCode = :provinceCode)
          AND (:activeOnly = false OR c.isActive = true)
        ORDER BY c.provinceCode ASC, c.wardCode ASC
        """,
    )
    fun search(
        @Param("serviceAreaId") serviceAreaId: UUID?,
        @Param("provinceCode") provinceCode: String,
        @Param("activeOnly") activeOnly: Boolean,
        pageable: Pageable,
    ): Page<ServiceAreaCoverage>

    @Query(
        """
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM ServiceAreaCoverage c
        WHERE c.isActive = true
          AND c.wardCode IS NULL
          AND c.provinceCode = :provinceCode
          AND (:excludedId IS NULL OR c.id <> :excludedId)
        """,
    )
    fun hasActiveProvinceConflict(
        @Param("provinceCode") provinceCode: String,
        @Param("excludedId") excludedId: UUID?,
    ): Boolean

    @Query(
        """
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM ServiceAreaCoverage c
        WHERE c.isActive = true
          AND c.wardCode = :wardCode
          AND (:excludedId IS NULL OR c.id <> :excludedId)
        """,
    )
    fun hasActiveWardConflict(
        @Param("wardCode") wardCode: String,
        @Param("excludedId") excludedId: UUID?,
    ): Boolean
}
