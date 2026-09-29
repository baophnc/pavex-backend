package com.pavex.shipment.dto

import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShipmentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/** Customer-facing list result. Internal UUIDs intentionally stay out of the API contract. */
data class ShipmentPageResponse(
    val items: List<ShipmentSummaryResponse>,
    val nextCursor: String?,
)

data class ShipmentSummaryResponse(
    val trackingCode: String,
    val merchantReference: String?,
    val status: ShipmentStatus,
    val serviceLevel: ShipmentServiceLevel,
    val shippingFee: Long,
    val codAmount: Long,
    val amountToCollect: Long,
    val destinationProvinceCode: String,
    val destinationProvinceName: String,
    val destinationWardCode: String,
    val destinationWardName: String,
    val originHubCode: String,
    val originHubName: String,
    val destinationHubCode: String,
    val destinationHubName: String,
    val parcelCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ShipmentDetailResponse(
    val trackingCode: String,
    val merchantReference: String?,
    val status: ShipmentStatus,
    val lifecycleStatus: ShipmentLifecycleStatus,
    val pickupAddressId: java.util.UUID?,
    val sender: ShipmentAddressResponse,
    val recipient: ShipmentAddressResponse,
    val shippingFeePayer: com.pavex.shipment.entity.ShippingFeePayer,
    val pricing: ShipmentPricingResponse,
    val collection: ShipmentCollectionResponse,
    val routing: ShipmentRoutingResponse,
    val parcels: List<ParcelResponse>,
    val timeline: List<ShipmentTimelineEventResponse>,
    val note: String?,
    val cancellationReason: String?,
    val cancelledAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ShipmentTimelineEventResponse(
    val status: ShipmentStatus,
    val code: String,
    val description: String,
    val parcelCode: String?,
    val locationName: String?,
    val occurredAt: Instant,
)

data class CancelShipmentRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String,
)

data class PublicTrackingResponse(
    val trackingCode: String,
    val parcelCode: String?,
    val status: String,
    val serviceLevel: ShipmentServiceLevel,
    val origin: PublicTrackingLocation,
    val destination: PublicTrackingLocation,
    val currentLocation: String?,
    val estimatedDeliveryFrom: Instant,
    val estimatedDeliveryTo: Instant,
    val parcelCount: Int,
    val timeline: List<PublicTrackingEvent>,
)

data class PublicTrackingLocation(
    val provinceCode: String,
    val provinceName: String,
    val wardCode: String,
    val wardName: String,
)

data class PublicTrackingEvent(
    val status: ShipmentStatus,
    val description: String,
    val locationName: String?,
    val occurredAt: Instant,
)
