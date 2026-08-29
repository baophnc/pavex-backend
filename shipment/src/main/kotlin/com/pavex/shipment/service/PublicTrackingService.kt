package com.pavex.shipment.service

import com.pavex.shipment.dto.PublicTrackingResponse
import com.pavex.shipment.entity.Parcel
import com.pavex.shipment.repository.ParcelRepository
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PublicTrackingService(
    private val shipments: ShipmentRepository,
    private val parcels: ParcelRepository,
) {
    @Transactional(readOnly = true)
    fun get(rawCode: String): PublicTrackingResponse? {
        val code = ShipmentCodeNormalizer.normalizeOrNull(rawCode) ?: return null
        val selectedParcel: Parcel?
        val shipment = if (code.contains('-')) {
            selectedParcel = parcels.findByCodeIgnoreCase(code) ?: return null
            selectedParcel.shipment
        } else {
            selectedParcel = null
            shipments.findByTrackingCodeIgnoreCase(code) ?: return null
        }
        return ShipmentResponseMapper.publicTracking(shipment, selectedParcel)
    }
}
