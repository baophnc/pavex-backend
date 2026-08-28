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

enum class RouteTemplateStatus { INACTIVE, ACTIVE }

@Entity
@Table(name = "route_templates", schema = "network")
class RouteTemplate(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 80)
    var code: String = "",
    @Column(nullable = false, length = 160)
    var name: String = "",
    @Column(length = 500)
    var description: String? = null,
    @Column(name = "origin_hub_id", nullable = false)
    var originHubId: UUID = UUID.randomUUID(),
    @Column(name = "destination_hub_id", nullable = false)
    var destinationHubId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var priority: Int = 100,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RouteTemplateStatus = RouteTemplateStatus.INACTIVE,
    @Column(nullable = false)
    var revision: Int = 1,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun update(
        name: String,
        description: String?,
        originHubId: UUID,
        destinationHubId: UUID,
        priority: Int,
    ) {
        this.name = name
        this.description = description
        this.originHubId = originHubId
        this.destinationHubId = destinationHubId
        this.priority = priority
        revision += 1
        updatedAt = Instant.now()
    }

    fun setActive(active: Boolean) {
        status = if (active) RouteTemplateStatus.ACTIVE else RouteTemplateStatus.INACTIVE
        updatedAt = Instant.now()
    }
}
