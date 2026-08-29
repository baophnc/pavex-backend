package com.pavex.shipment.service

import com.pavex.shipment.dto.ParcelResponse
import com.pavex.shipment.dto.ShipmentAddressResponse
import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.dto.ShipmentPricingResponse
import com.pavex.shipment.dto.ShipmentRoutingResponse
import com.pavex.shipment.entity.Shipment

object ShipmentResponseMapper {
    fun created(shipment: Shipment): ShipmentCreatedResponse = ShipmentCreatedResponse(
        trackingCode = shipment.trackingCode,
        merchantReference = shipment.merchantReference,
        status = shipment.status,
        sender = ShipmentAddressResponse(
            shipment.sender.contactName,
            shipment.sender.contactPhone,
            shipment.sender.addressLine,
            shipment.sender.provinceCode,
            shipment.sender.provinceName,
            shipment.sender.wardCode,
            shipment.sender.wardName,
        ),
        recipient = ShipmentAddressResponse(
            shipment.recipient.contactName,
            shipment.recipient.contactPhone,
            shipment.recipient.addressLine,
            shipment.recipient.provinceCode,
            shipment.recipient.provinceName,
            shipment.recipient.wardCode,
            shipment.recipient.wardName,
        ),
        shippingFeePayer = shipment.shippingFeePayer,
        pricing = ShipmentPricingResponse(
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
        ),
        routing = ShipmentRoutingResponse(
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
        ),
        parcels = shipment.parcels.sortedBy { it.sequence }.map { parcel ->
            ParcelResponse(
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
            )
        },
        note = shipment.note,
        createdAt = shipment.createdAt,
    )
}
