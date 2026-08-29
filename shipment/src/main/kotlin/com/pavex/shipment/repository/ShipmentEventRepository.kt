package com.pavex.shipment.repository

import com.pavex.shipment.entity.ShipmentEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ShipmentEventRepository : JpaRepository<ShipmentEvent, UUID> {
    fun findAllByShipment_IdOrderByOccurredAtAsc(shipmentId: UUID): List<ShipmentEvent>
}
