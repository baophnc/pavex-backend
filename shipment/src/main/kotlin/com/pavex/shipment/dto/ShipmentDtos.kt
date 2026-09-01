package com.pavex.shipment.dto

import com.pavex.shipment.entity.GoodsCategory
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentCoverageTier
import com.pavex.shipment.entity.ShipmentPricingZone
import com.pavex.shipment.entity.ShipmentRoutingStatus
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShipmentStatus
import com.pavex.shipment.entity.ShippingFeePayer
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateShipmentRequest(
    val quoteId: UUID,
    @field:Size(max = 100)
    val merchantReference: String? = null,
    @field:Valid
    val sender: ShipmentAddressRequest,
    @field:Valid
    val recipient: ShipmentAddressRequest,
    val shippingFeePayer: ShippingFeePayer,
    @field:Size(min = 1, max = 20)
    @field:Valid
    val parcels: List<ParcelContentsRequest>,
    @field:Size(max = 1000)
    val note: String? = null,
)

data class ShipmentAddressRequest(
    @field:NotBlank @field:Size(max = 120)
    val contactName: String,
    @field:NotBlank @field:Size(max = 20)
    val contactPhone: String,
    @field:NotBlank @field:Size(max = 500)
    val addressLine: String,
    @field:NotBlank @field:Size(max = 20)
    val provinceCode: String,
    @field:NotBlank @field:Size(max = 20)
    val wardCode: String,
)

data class ParcelContentsRequest(
    @field:NotBlank @field:Size(max = 500)
    val description: String,
    @field:Min(1) @field:Max(10000)
    val quantity: Int,
    val category: GoodsCategory,
    val isFragile: Boolean = false,
)

data class ShipmentCreatedResponse(
    val trackingCode: String,
    val merchantReference: String?,
    val status: ShipmentStatus,
    val sender: ShipmentAddressResponse,
    val recipient: ShipmentAddressResponse,
    val shippingFeePayer: ShippingFeePayer,
    val pricing: ShipmentPricingResponse,
    val routing: ShipmentRoutingResponse,
    val parcels: List<ParcelResponse>,
    val note: String?,
    val createdAt: Instant,
)

data class ShipmentAddressResponse(
    val contactName: String,
    val contactPhone: String,
    val addressLine: String,
    val provinceCode: String,
    val provinceName: String,
    val wardCode: String,
    val wardName: String,
)

data class ShipmentPricingResponse(
    val rateCode: String,
    val rateVersion: String,
    val currency: String,
    val serviceLevel: ShipmentServiceLevel,
    val pricingZone: ShipmentPricingZone,
    val baseFee: Long,
    val additionalWeightFee: Long,
    val areaSurcharge: Long,
    val codFee: Long,
    val insuranceFee: Long,
    val shippingFee: Long,
    val codAmount: Long,
    val declaredValue: Long,
    val estimatedMinDays: Int,
    val estimatedMaxDays: Int,
)

data class ShipmentRoutingResponse(
    val originHubCode: String,
    val originHubName: String,
    val originCoverageTier: ShipmentCoverageTier,
    val destinationHubCode: String,
    val destinationHubName: String,
    val destinationCoverageTier: ShipmentCoverageTier,
    val routeRevision: Int,
    val reservedWeightKg: BigDecimal,
    val estimatedDeliveryAt: Instant?,
    val estimatedTransitMinutes: Int?,
    val distanceKm: BigDecimal?,
    val status: ShipmentRoutingStatus,
)

data class ParcelResponse(
    val id: UUID,
    val code: String,
    val sequence: Int,
    val description: String,
    val quantity: Int,
    val category: GoodsCategory,
    val isFragile: Boolean,
    val actualWeightKg: BigDecimal,
    val volumetricWeightKg: BigDecimal,
    val chargeableWeightKg: BigDecimal,
    val lengthCm: BigDecimal,
    val widthCm: BigDecimal,
    val heightCm: BigDecimal,
    val status: ParcelStatus,
)
