package com.pavex.shipment.dto

import com.pavex.shipment.entity.GoodsCategory
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShippingFeePayer
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

/**
 * A slice of a shop's spreadsheet upload. [batchId] is chosen by the client for the whole file and stays the same when
 * a slice is sent again, so a retried row returns the order it already created instead of a second one.
 */
data class BulkCreateShipmentsRequest(
    val batchId: UUID,
    @field:Size(min = 1, max = 50)
    val rows: List<@Valid BulkShipmentRowRequest>,
)

/** One order: one parcel, priced here with the requested service level, sent from one of the shop's pickup addresses. */
data class BulkShipmentRowRequest(
    /** The spreadsheet row, echoed in the result and part of the row's idempotency key. */
    @field:Min(1) @field:Max(100_000)
    val rowNumber: Int,
    /**
     * Chosen by the client from the row's content (and how many identical rows came before it), so uploading the
     * same rows again, in this file or a corrected one, returns the orders already created instead of new ones.
     * Without it the row is keyed by [BulkCreateShipmentsRequest.batchId] and [rowNumber].
     */
    @field:Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
    val clientKey: String? = null,
    @field:Size(max = 100)
    val merchantReference: String? = null,
    val pickupAddressId: UUID,
    @field:Valid
    val recipient: ShipmentAddressRequest,
    @field:Valid
    val parcel: BulkParcelRequest,
    @field:PositiveOrZero
    val declaredValue: Long = 0,
    @field:PositiveOrZero
    val codAmount: Long = 0,
    val serviceLevel: ShipmentServiceLevel,
    val shippingFeePayer: ShippingFeePayer,
    @field:Size(max = 1000)
    val note: String? = null,
)

data class BulkParcelRequest(
    @field:NotBlank @field:Size(max = 500)
    val description: String,
    @field:Min(1) @field:Max(10000)
    val quantity: Int,
    val category: GoodsCategory,
    val isFragile: Boolean = false,
    @field:Positive
    val weightKg: BigDecimal,
    @field:Positive
    val lengthCm: BigDecimal,
    @field:Positive
    val widthCm: BigDecimal,
    @field:Positive
    val heightCm: BigDecimal,
)

enum class BulkShipmentRowStatus { CREATED, FAILED }

data class BulkShipmentRowResult(
    val rowNumber: Int,
    val status: BulkShipmentRowStatus,
    val trackingCode: String? = null,
    val shippingFee: Long? = null,
    /** The order was created by an earlier send of the same batch. */
    val replayed: Boolean = false,
    val code: String? = null,
    val message: String? = null,
)

data class BulkCreateShipmentsResponse(
    val created: Int,
    val failed: Int,
    val results: List<BulkShipmentRowResult>,
)
