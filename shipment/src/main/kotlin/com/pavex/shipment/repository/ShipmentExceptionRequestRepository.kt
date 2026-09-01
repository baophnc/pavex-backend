package com.pavex.shipment.repository

import com.pavex.shipment.entity.ShipmentExceptionRequest
import com.pavex.shipment.entity.ShipmentExceptionRequestKind
import com.pavex.shipment.entity.ShipmentExceptionRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ShipmentExceptionRequestRepository : JpaRepository<ShipmentExceptionRequest, UUID> {
    fun findAllByShipmentIdOrderByRequestedAtAsc(shipmentId: UUID): List<ShipmentExceptionRequest>
    fun findByIdAndShipmentId(id: UUID, shipmentId: UUID): ShipmentExceptionRequest?
    fun existsByShipmentIdAndKindAndStatusIn(
        shipmentId: UUID,
        kind: ShipmentExceptionRequestKind,
        statuses: Collection<ShipmentExceptionRequestStatus>,
    ): Boolean
    fun existsByShipmentIdAndStatusIn(
        shipmentId: UUID,
        statuses: Collection<ShipmentExceptionRequestStatus>,
    ): Boolean
}
