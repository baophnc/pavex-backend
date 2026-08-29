package com.pavex.shipment.repository

import com.pavex.shipment.entity.DeliveryAttempt
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeliveryAttemptRepository : JpaRepository<DeliveryAttempt, UUID> {
    fun findByParcelIdAndIdempotencyKey(parcelId: UUID, idempotencyKey: String): DeliveryAttempt?
    fun countByParcelId(parcelId: UUID): Long
    fun findAllByParcelIdOrderByAttemptNumberAsc(parcelId: UUID): List<DeliveryAttempt>
}
