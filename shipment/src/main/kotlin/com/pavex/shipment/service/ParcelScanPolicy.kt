package com.pavex.shipment.service

import com.pavex.contracts.network.ShipmentOperationalHubSnapshot
import com.pavex.shipment.entity.ParcelStatus
import java.util.UUID

object ParcelScanPolicy {
    fun isHubAllowed(
        originHubId: UUID,
        destinationHubId: UUID,
        hub: ShipmentOperationalHubSnapshot,
        nextStatus: ParcelStatus?,
    ): Boolean {
        if (!hub.active) return false
        if (nextStatus == null) return true

        return when (nextStatus) {
            ParcelStatus.PICKED_UP,
            ParcelStatus.AT_ORIGIN_HUB,
            -> hub.hubId == originHubId

            ParcelStatus.AT_DESTINATION_HUB,
            ParcelStatus.OUT_FOR_DELIVERY,
            ParcelStatus.DELIVERY_FAILED,
            ParcelStatus.DELIVERED,
            -> hub.hubId == destinationHubId

            ParcelStatus.AT_TRANSIT_HUB ->
                hub.onReservedRoute && hub.hubId != originHubId && hub.hubId != destinationHubId

            ParcelStatus.RETURNED -> hub.hubId == originHubId

            ParcelStatus.IN_TRANSIT,
            ParcelStatus.RETURNING,
            -> hub.hubId == originHubId || hub.hubId == destinationHubId || hub.onReservedRoute

            ParcelStatus.AWAITING_PICKUP,
            ParcelStatus.CANCELLED,
            -> false
        }
    }
}
