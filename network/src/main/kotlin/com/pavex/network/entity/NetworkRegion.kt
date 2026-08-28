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

enum class NetworkRegionStatus { INACTIVE, ACTIVE }

@Entity
@Table(name = "regions", schema = "network")
class NetworkRegion(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 50)
    var code: String = "",
    @Column(nullable = false, length = 160)
    var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: NetworkRegionStatus = NetworkRegionStatus.ACTIVE,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun update(name: String) {
        this.name = name.trim()
        updatedAt = Instant.now()
    }

    fun setActive(active: Boolean) {
        status = if (active) NetworkRegionStatus.ACTIVE else NetworkRegionStatus.INACTIVE
        updatedAt = Instant.now()
    }
}
