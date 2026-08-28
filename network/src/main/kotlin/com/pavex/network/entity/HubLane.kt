package com.pavex.network.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class TransportMode { ROAD, AIR, RAIL, SEA }
enum class HubLaneStatus { INACTIVE, ACTIVE }

@Entity
@Table(name = "hub_lanes", schema = "network")
class HubLane(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 50)
    var code: String = "",
    @Column(name = "from_hub_id", nullable = false)
    var fromHubId: UUID = UUID.randomUUID(),
    @Column(name = "to_hub_id", nullable = false)
    var toHubId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 20)
    var transportMode: TransportMode = TransportMode.ROAD,
    @Column(name = "estimated_transit_minutes", nullable = false)
    var estimatedTransitMinutes: Int = 1,
    @Column(name = "distance_km", precision = 10, scale = 2)
    var distanceKm: BigDecimal? = null,
    @Column(name = "max_shipment_weight_kg", nullable = false, precision = 12, scale = 3)
    var maxShipmentWeightKg: BigDecimal = BigDecimal.ONE,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: HubLaneStatus = HubLaneStatus.INACTIVE,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun update(
        fromHubId: UUID,
        toHubId: UUID,
        transportMode: TransportMode,
        estimatedTransitMinutes: Int,
        distanceKm: BigDecimal?,
        maxShipmentWeightKg: BigDecimal,
    ) {
        this.fromHubId = fromHubId
        this.toHubId = toHubId
        this.transportMode = transportMode
        this.estimatedTransitMinutes = estimatedTransitMinutes
        this.distanceKm = distanceKm
        this.maxShipmentWeightKg = maxShipmentWeightKg
        updatedAt = Instant.now()
    }

    fun setActive(active: Boolean) {
        status = if (active) HubLaneStatus.ACTIVE else HubLaneStatus.INACTIVE
        updatedAt = Instant.now()
    }
}
