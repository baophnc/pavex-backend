package com.pavex.network.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class ServiceAreaStatus { INACTIVE, ACTIVE }

@Entity
@Table(name = "service_areas", schema = "network")
class ServiceArea(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 50)
    var code: String = "",
    @Column(nullable = false, length = 200)
    var name: String = "",
    @Column(name = "region_id", nullable = false)
    var regionId: UUID = UUID.randomUUID(),
    @Column(name = "primary_hub_id", nullable = false)
    var primaryHubId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ServiceAreaStatus = ServiceAreaStatus.ACTIVE,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun update(name: String, regionId: UUID, primaryHubId: UUID) {
        this.name = name.trim()
        this.regionId = regionId
        this.primaryHubId = primaryHubId
        updatedAt = Instant.now()
    }

    fun setActive(active: Boolean) {
        status = if (active) ServiceAreaStatus.ACTIVE else ServiceAreaStatus.INACTIVE
        updatedAt = Instant.now()
    }
}
