package com.pavex.shipment.repository

import com.pavex.shipment.entity.ShipmentOutboxEvent
import com.pavex.shipment.entity.ShipmentOutboxStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ShipmentOutboxEventRepository : JpaRepository<ShipmentOutboxEvent, UUID> {
    fun countByStatus(status: ShipmentOutboxStatus): Long
}
