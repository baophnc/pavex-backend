package com.pavex.network.repository

import com.pavex.network.entity.NetworkRegion
import com.pavex.network.entity.NetworkRegionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NetworkRegionRepository : JpaRepository<NetworkRegion, UUID> {
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun findByCodeIgnoreCase(code: String): NetworkRegion?
    fun findAllByOrderByNameAsc(): List<NetworkRegion>
    fun findAllByStatusOrderByNameAsc(status: NetworkRegionStatus): List<NetworkRegion>
}
