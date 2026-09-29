package com.pavex.shipment.service

import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class ShipmentCreationWriter(
    private val shipments: ShipmentRepository,
    private val outbox: ShipmentOutboxService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun findExisting(createdByUserId: UUID, idempotencyKey: String): ExistingShipmentCreation? {
        val shipment = shipments.findByCreatedByUserIdAndIdempotencyKey(createdByUserId, idempotencyKey)
            ?: return null
        return ExistingShipmentCreation(
            requestFingerprint = shipment.requestFingerprint,
            response = ShipmentResponseMapper.created(shipment),
        )
    }

    @Transactional(readOnly = true)
    fun isQuoteUsed(quoteId: UUID): Boolean = shipments.existsByQuoteId(quoteId)

    /** The active shipment already using this merchant reference, if any. */
    @Transactional(readOnly = true)
    fun shipmentWithMerchantReference(merchantId: UUID, reference: String): String? =
        shipments.findActiveTrackingCodesByMerchantReference(merchantId, reference).firstOrNull()

    @Transactional
    fun persist(shipment: Shipment): ShipmentCreatedResponse {
        val saved = shipments.saveAndFlush(shipment)
        outbox.enqueue(
            aggregateId = saved.id,
            aggregateCode = saved.trackingCode,
            eventType = "shipment.created",
            payloadJson = objectMapper.writeValueAsString(
                ShipmentCreatedOutboxPayload(
                    shipmentId = saved.id,
                    trackingCode = saved.trackingCode,
                    merchantId = saved.merchantId,
                    createdByUserId = saved.createdByUserId,
                    quoteId = saved.quoteId,
                    parcelCodes = saved.parcels.sortedBy { it.sequence }.map { it.code },
                    originHubId = saved.originHubId,
                    destinationHubId = saved.destinationHubId,
                    routeRevision = saved.routeRevision,
                    capacityReservationBatchId = saved.capacityReservationBatchId,
                    occurredAt = saved.createdAt,
                ),
            ),
            occurredAt = saved.createdAt,
        )
        return ShipmentResponseMapper.created(saved)
    }
}

data class ExistingShipmentCreation(
    val requestFingerprint: String,
    val response: ShipmentCreatedResponse,
)

private data class ShipmentCreatedOutboxPayload(
    val shipmentId: UUID,
    val trackingCode: String,
    val merchantId: UUID,
    val createdByUserId: UUID,
    val quoteId: UUID,
    val parcelCodes: List<String>,
    val originHubId: UUID,
    val destinationHubId: UUID,
    val routeRevision: Int,
    val capacityReservationBatchId: UUID?,
    val occurredAt: Instant,
)
