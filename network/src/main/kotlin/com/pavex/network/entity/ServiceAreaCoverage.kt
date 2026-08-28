package com.pavex.network.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class CoverageTier { STANDARD, REMOTE, ISLAND }

@Entity
@Table(name = "service_area_coverages", schema = "network")
class ServiceAreaCoverage(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "service_area_id", nullable = false)
    var serviceAreaId: UUID = UUID.randomUUID(),
    @Column(name = "province_code", nullable = false, length = 20)
    var provinceCode: String = "",
    @Column(name = "ward_code", length = 20)
    var wardCode: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var tier: CoverageTier = CoverageTier.STANDARD,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun update(
        serviceAreaId: UUID,
        provinceCode: String,
        wardCode: String?,
        tier: CoverageTier,
    ) {
        this.serviceAreaId = serviceAreaId
        this.provinceCode = provinceCode.trim()
        this.wardCode = wardCode?.trim()?.takeIf { it.isNotBlank() }
        this.tier = tier
        updatedAt = Instant.now()
    }

    fun setActive(active: Boolean) {
        isActive = active
        updatedAt = Instant.now()
    }
}
