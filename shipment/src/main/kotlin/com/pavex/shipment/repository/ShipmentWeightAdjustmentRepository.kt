package com.pavex.shipment.repository

import com.pavex.shipment.entity.ShipmentWeightAdjustment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ShipmentWeightAdjustmentRepository : JpaRepository<ShipmentWeightAdjustment, UUID> {
    fun findAllByShipmentIdOrderByAdjustedAtAsc(shipmentId: UUID): List<ShipmentWeightAdjustment>
    fun findByIdAndShipmentId(id: UUID, shipmentId: UUID): ShipmentWeightAdjustment?
    fun existsByShipmentIdAndReviewCompletedFalse(shipmentId: UUID): Boolean
}
