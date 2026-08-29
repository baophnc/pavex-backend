package com.pavex.shipment.repository

import com.pavex.shipment.entity.Shipment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ShipmentRepository : JpaRepository<Shipment, UUID> {
    fun findByTrackingCodeIgnoreCase(trackingCode: String): Shipment?
    fun findByTrackingCodeIgnoreCaseAndCreatedByUserId(trackingCode: String, createdByUserId: UUID): Shipment?
    fun findByQuoteId(quoteId: UUID): Shipment?
    fun findByCreatedByUserIdAndIdempotencyKey(createdByUserId: UUID, idempotencyKey: String): Shipment?
    fun existsByQuoteId(quoteId: UUID): Boolean
}
