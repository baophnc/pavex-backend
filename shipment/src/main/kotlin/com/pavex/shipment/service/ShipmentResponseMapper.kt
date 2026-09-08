package com.pavex.shipment.service

import com.pavex.shipment.dto.ParcelResponse
import com.pavex.shipment.dto.PublicTrackingEvent
import com.pavex.shipment.dto.PublicTrackingLocation
import com.pavex.shipment.dto.PublicTrackingResponse
import com.pavex.shipment.dto.ShipmentAddressResponse
import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.dto.ShipmentPricingResponse
import com.pavex.shipment.dto.ShipmentRoutingResponse
import com.pavex.shipment.dto.ShipmentSummaryResponse
import com.pavex.shipment.dto.ShipmentTimelineEventResponse
import com.pavex.shipment.entity.Parcel
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentAddressSnapshot
import java.time.temporal.ChronoUnit

object ShipmentResponseMapper {
    fun created(shipment: Shipment): ShipmentCreatedResponse = ShipmentCreatedResponse(
        trackingCode = shipment.trackingCode,
        merchantReference = shipment.merchantReference,
        status = shipment.status,
        sender = address(shipment.sender),
        recipient = address(shipment.recipient),
        shippingFeePayer = shipment.shippingFeePayer,
        pricing = pricing(shipment),
        routing = routing(shipment),
        parcels = parcels(shipment),
        note = shipment.note,
        createdAt = shipment.createdAt,
    )

    fun summary(shipment: Shipment): ShipmentSummaryResponse = ShipmentSummaryResponse(
        trackingCode = shipment.trackingCode,
        merchantReference = shipment.merchantReference,
        status = shipment.status,
        serviceLevel = shipment.serviceLevel,
        shippingFee = shipment.shippingFee,
        codAmount = shipment.codAmount,
        destinationProvinceCode = shipment.recipient.provinceCode,
        destinationProvinceName = shipment.recipient.provinceName,
        destinationWardCode = shipment.recipient.wardCode,
        destinationWardName = shipment.recipient.wardName,
        originHubCode = shipment.originHubCode,
        originHubName = shipment.originHubName,
        destinationHubCode = shipment.destinationHubCode,
        destinationHubName = shipment.destinationHubName,
        parcelCount = shipment.parcels.size,
        createdAt = shipment.createdAt,
        updatedAt = shipment.updatedAt,
    )

    fun detail(shipment: Shipment): ShipmentDetailResponse {
        val parcelCodes = shipment.parcels.associate { it.id to it.code }
        return ShipmentDetailResponse(
            trackingCode = shipment.trackingCode,
            merchantReference = shipment.merchantReference,
            status = shipment.status,
            lifecycleStatus = shipment.lifecycleStatus,
            sender = address(shipment.sender),
            recipient = address(shipment.recipient),
            shippingFeePayer = shipment.shippingFeePayer,
            pricing = pricing(shipment),
            routing = routing(shipment),
            parcels = parcels(shipment),
            timeline = shipment.events
                .asSequence()
                .filter { ShipmentEventVisibility.isCustomerVisible(it.code) }
                .sortedWith(compareBy({ it.occurredAt }, { it.recordedAt }))
                .map { event ->
                    ShipmentTimelineEventResponse(
                        status = event.status,
                        code = event.code,
                        description = ShipmentVietnameseText.event(event.description),
                        parcelCode = event.parcelId?.let(parcelCodes::get),
                        locationName = event.locationName,
                        occurredAt = event.occurredAt,
                    )
                }
                .toList(),
            note = shipment.note,
            cancellationReason = shipment.cancellationReason,
            cancelledAt = shipment.cancelledAt,
            completedAt = shipment.completedAt,
            createdAt = shipment.createdAt,
            updatedAt = shipment.updatedAt,
        )
    }

    fun publicTracking(shipment: Shipment, selectedParcel: Parcel?): PublicTrackingResponse {
        val events = shipment.events
            .asSequence()
            .filter { ShipmentEventVisibility.isCustomerVisible(it.code) }
            .filter { event -> selectedParcel == null || event.parcelId == null || event.parcelId == selectedParcel.id }
            .sortedWith(compareByDescending<com.pavex.shipment.entity.ShipmentEvent> { it.occurredAt }
                .thenByDescending { it.recordedAt })
            .toList()
        val latestLocation = events.firstOrNull { !it.locationName.isNullOrBlank() }?.locationName

        return PublicTrackingResponse(
            trackingCode = shipment.trackingCode,
            parcelCode = selectedParcel?.code,
            status = selectedParcel?.status?.name ?: shipment.status.name,
            serviceLevel = shipment.serviceLevel,
            origin = PublicTrackingLocation(
                provinceCode = shipment.sender.provinceCode,
                provinceName = shipment.sender.provinceName,
                wardCode = shipment.sender.wardCode,
                wardName = shipment.sender.wardName,
            ),
            destination = PublicTrackingLocation(
                provinceCode = shipment.recipient.provinceCode,
                provinceName = shipment.recipient.provinceName,
                wardCode = shipment.recipient.wardCode,
                wardName = shipment.recipient.wardName,
            ),
            currentLocation = latestLocation,
            estimatedDeliveryFrom = shipment.createdAt.plus(shipment.estimatedMinDays.toLong(), ChronoUnit.DAYS),
            estimatedDeliveryTo = shipment.createdAt.plus(shipment.estimatedMaxDays.toLong(), ChronoUnit.DAYS),
            parcelCount = shipment.parcels.size,
            timeline = events.map { event ->
                PublicTrackingEvent(
                    status = event.status,
                    description = ShipmentVietnameseText.event(event.description),
                    locationName = event.locationName,
                    occurredAt = event.occurredAt,
                )
            },
        )
    }

    private fun address(snapshot: ShipmentAddressSnapshot) = ShipmentAddressResponse(
        contactName = snapshot.contactName,
        contactPhone = snapshot.contactPhone,
        addressLine = snapshot.addressLine,
        provinceCode = snapshot.provinceCode,
        provinceName = snapshot.provinceName,
        wardCode = snapshot.wardCode,
        wardName = snapshot.wardName,
    )

    private fun pricing(shipment: Shipment) = ShipmentPricingResponse(
        rateCode = shipment.rateCode,
        rateVersion = shipment.rateVersion,
        currency = shipment.currency,
        serviceLevel = shipment.serviceLevel,
        pricingZone = shipment.pricingZone,
        baseFee = shipment.baseFee,
        additionalWeightFee = shipment.additionalWeightFee,
        areaSurcharge = shipment.areaSurcharge,
        codFee = shipment.codFee,
        insuranceFee = shipment.insuranceFee,
        shippingFee = shipment.shippingFee,
        codAmount = shipment.codAmount,
        declaredValue = shipment.declaredValue,
        estimatedMinDays = shipment.estimatedMinDays,
        estimatedMaxDays = shipment.estimatedMaxDays,
    )

    private fun routing(shipment: Shipment) = ShipmentRoutingResponse(
        originHubCode = shipment.originHubCode,
        originHubName = shipment.originHubName,
        originCoverageTier = shipment.originCoverageTier,
        destinationHubCode = shipment.destinationHubCode,
        destinationHubName = shipment.destinationHubName,
        destinationCoverageTier = shipment.destinationCoverageTier,
        routeRevision = shipment.routeRevision,
        reservedWeightKg = shipment.reservedWeightKg,
        estimatedDeliveryAt = shipment.estimatedDeliveryAt,
        estimatedTransitMinutes = shipment.estimatedTransitMinutes,
        distanceKm = shipment.routeDistanceKm,
        status = shipment.routingStatus,
    )

    private fun parcels(shipment: Shipment) = shipment.parcels.sortedBy { it.sequence }.map { parcel ->
        ParcelResponse(
            id = parcel.id,
            code = parcel.code,
            sequence = parcel.sequence,
            description = parcel.description,
            quantity = parcel.quantity,
            category = parcel.category,
            isFragile = parcel.isFragile,
            actualWeightKg = parcel.actualWeightKg,
            volumetricWeightKg = parcel.volumetricWeightKg,
            chargeableWeightKg = parcel.chargeableWeightKg,
            lengthCm = parcel.lengthCm,
            widthCm = parcel.widthCm,
            heightCm = parcel.heightCm,
            status = parcel.status,
            currentHubId = parcel.currentHubId,
            currentRouteLegId = parcel.currentRouteLegId,
        )
    }
}
