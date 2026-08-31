package com.pavex.shipment.repository

import com.pavex.shipment.entity.ShipmentControlSignal
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface ShipmentControlSignalRepository : JpaRepository<ShipmentControlSignal, UUID> {
    fun findAllByShipmentIdInAndOccurredAtGreaterThanEqual(
        shipmentIds: Collection<UUID>,
        occurredAt: Instant,
    ): List<ShipmentControlSignal>
}
