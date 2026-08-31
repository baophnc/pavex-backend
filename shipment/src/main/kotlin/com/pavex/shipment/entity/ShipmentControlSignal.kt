package com.pavex.shipment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class ShipmentControlSignalType { ROUTE_DEVIATION }

@Entity
@Table(name = "shipment_control_signals", schema = "shipments")
class ShipmentControlSignal(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "parcel_id")
    var parcelId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 40)
    var type: ShipmentControlSignalType = ShipmentControlSignalType.ROUTE_DEVIATION,
    @Column(nullable = false, length = 80)
    var code: String = "",
    @Column(name = "hub_id", nullable = false)
    var hubId: UUID = UUID.randomUUID(),
    @Column(name = "route_leg_id")
    var routeLegId: UUID? = null,
    @Column(name = "actor_user_id")
    var actorUserId: UUID? = null,
    @Column(nullable = false, length = 500)
    var description: String = "",
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
