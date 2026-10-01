package com.pavex.shipment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "shipment_events", schema = "shipments")
class ShipmentEvent(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    var shipment: Shipment = Shipment(),
    @Column(name = "parcel_id")
    var parcelId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ShipmentStatus = ShipmentStatus.AWAITING_PICKUP,
    @Column(nullable = false, length = 100)
    var code: String = "",
    @Column(nullable = false, length = 500)
    var description: String = "",
    @Column(name = "actor_user_id")
    var actorUserId: UUID? = null,
    @Column(name = "hub_id")
    var hubId: UUID? = null,
    @Column(name = "location_name", length = 200)
    var locationName: String? = null,
    @Column(name = "data_json", columnDefinition = "text")
    var dataJson: String? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Instant = Instant.now(),
) {
    companion object {
        fun created(shipment: Shipment, actorUserId: UUID, occurredAt: Instant): ShipmentEvent =
            ShipmentEvent(
                shipment = shipment,
                status = ShipmentStatus.AWAITING_PICKUP,
                code = "shipment.created",
                description = "Đã tạo đơn hàng, chờ nhân viên lấy hàng.",
                actorUserId = actorUserId,
                locationName = shipment.sender.wardName,
                occurredAt = occurredAt,
                recordedAt = occurredAt,
            )

        fun acceptedAtCounter(shipment: Shipment, actorUserId: UUID, occurredAt: Instant): ShipmentEvent =
            ShipmentEvent(
                shipment = shipment,
                status = ShipmentStatus.AT_ORIGIN_HUB,
                code = "shipment.counter-accepted",
                description = "Đã nhận gửi hàng tại quầy bưu cục.",
                actorUserId = actorUserId,
                hubId = shipment.originHubId,
                locationName = shipment.originHubName,
                occurredAt = occurredAt,
                recordedAt = occurredAt,
            )
    }
}
