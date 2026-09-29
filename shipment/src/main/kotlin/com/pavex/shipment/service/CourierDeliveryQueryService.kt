package com.pavex.shipment.service

import com.pavex.shipment.dto.CourierDeliveryResponse
import com.pavex.shipment.dto.CourierParcelResponse
import com.pavex.shipment.entity.ParcelCustodyType
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.repository.ParcelRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** What a courier is carrying right now, grouped by shipment, with the money to collect. */
@Service
class CourierDeliveryQueryService(
    private val parcels: ParcelRepository,
) {
    @Transactional(readOnly = true)
    fun mine(courierUserId: UUID): List<CourierDeliveryResponse> =
        parcels.findAllByCustodyTypeAndCustodyReferenceIdAndStatusIn(ParcelCustodyType.COURIER, courierUserId, HELD_STATUSES)
            .filter { it.shipment.lifecycleStatus == ShipmentLifecycleStatus.ACTIVE }
            .groupBy { it.shipment.id }
            .values
            .map { held ->
                val shipment = held.first().shipment
                CourierDeliveryResponse(
                    trackingCode = shipment.trackingCode,
                    status = shipment.status,
                    serviceLevel = shipment.serviceLevel,
                    recipient = ShipmentResponseMapper.address(shipment.recipient),
                    note = shipment.note,
                    amountToCollect = shipment.recipientCollectAmount,
                    collectionStatus = shipment.collectionStatus(),
                    parcelCount = shipment.parcels.size,
                    parcels = held.sortedBy { it.sequence }.map { parcel ->
                        CourierParcelResponse(
                            parcelCode = parcel.code,
                            sequence = parcel.sequence,
                            status = parcel.status,
                            description = parcel.description,
                            isFragile = parcel.isFragile,
                            assignmentId = parcel.currentAssignmentId,
                        )
                    },
                )
            }
            .sortedBy { it.trackingCode }

    companion object {
        /** Parcels still in the courier's hands: out for delivery, or failed and not yet back at a hub. */
        private val HELD_STATUSES = listOf(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERY_FAILED)
    }
}
