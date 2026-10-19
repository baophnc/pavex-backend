package com.pavex.shipment.service

import com.pavex.contracts.partners.MerchantPickupAddressSnapshot
import com.pavex.shipment.client.PartnerShipmentClient
import com.pavex.shipment.client.PricingQuoteClient
import com.pavex.shipment.client.PricingQuoteParcel
import com.pavex.shipment.client.PricingQuoteRequest
import com.pavex.shipment.dto.BulkCreateShipmentsRequest
import com.pavex.shipment.dto.BulkCreateShipmentsResponse
import com.pavex.shipment.dto.BulkShipmentRowRequest
import com.pavex.shipment.dto.BulkShipmentRowResult
import com.pavex.shipment.dto.BulkShipmentRowStatus
import com.pavex.shipment.dto.CreateShipmentRequest
import com.pavex.shipment.dto.ParcelContentsRequest
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentApiException
import com.pavex.shipment.exception.ShipmentForbiddenException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Creates a shop's orders from a spreadsheet, a slice at a time. Each row is priced with the service level it asks
 * for and then booked exactly like an order from the shop's form, so every rule of [CreateShipmentService] applies.
 * One row failing never stops the others: the result says, per row, which order was created or why not.
 */
@Service
class BulkCreateShipmentService(
    private val partners: PartnerShipmentClient,
    private val pricing: PricingQuoteClient,
    private val createShipment: CreateShipmentService,
    private val writer: ShipmentCreationWriter,
) {
    fun create(actorUserId: UUID, request: BulkCreateShipmentsRequest): BulkCreateShipmentsResponse {
        val capability = partners.orderCapability(actorUserId)
        val merchantId = capability.merchantId
        if (!capability.canCreateOrders || merchantId == null) {
            throw ShipmentForbiddenException("merchant_cannot_create_orders", "This account cannot create orders yet.")
        }
        val pickups = mutableMapOf<UUID, MerchantPickupAddressSnapshot?>()
        val results = request.rows.map { row ->
            try {
                createRow(actorUserId, merchantId, request.batchId, row, pickups)
            } catch (exception: ShipmentApiException) {
                failed(row, exception.code, exception.message)
            } catch (exception: RuntimeException) {
                log.warn("Bulk order row {} of batch {} failed unexpectedly", row.rowNumber, request.batchId, exception)
                failed(row, "unexpected_error", "The order could not be created. Please try again.")
            }
        }
        return BulkCreateShipmentsResponse(
            created = results.count { it.status == BulkShipmentRowStatus.CREATED },
            failed = results.count { it.status == BulkShipmentRowStatus.FAILED },
            results = results,
        )
    }

    private fun createRow(
        actorUserId: UUID,
        merchantId: UUID,
        batchId: UUID,
        row: BulkShipmentRowRequest,
        pickups: MutableMap<UUID, MerchantPickupAddressSnapshot?>,
    ): BulkShipmentRowResult {
        val idempotencyKey = row.clientKey?.let { "bulk-$it" } ?: idempotencyKey(batchId, row.rowNumber)
        // Sent again (a retried slice): the order exists, and a fresh quote would only make the replay conflict.
        writer.findExisting(actorUserId, idempotencyKey)?.let { existing ->
            return created(row, existing.response.trackingCode, existing.response.pricing.shippingFee, replayed = true)
        }
        val pickup = pickups.getOrPut(row.pickupAddressId) { partners.pickupAddress(merchantId, row.pickupAddressId) }
            ?: throw InvalidShipmentRequestException(
                "pickup_address_not_found",
                "The pickup address was not found among this merchant's pickup addresses.",
            )
        val quote = pricing.quote(
            PricingQuoteRequest(
                originProvinceCode = pickup.provinceCode,
                originWardCode = pickup.wardCode,
                destinationProvinceCode = row.recipient.provinceCode,
                destinationWardCode = row.recipient.wardCode,
                codAmount = row.codAmount,
                declaredValue = row.declaredValue,
                parcels = listOf(
                    PricingQuoteParcel(row.parcel.weightKg, row.parcel.lengthCm, row.parcel.widthCm, row.parcel.heightCm),
                ),
            ),
        )
        val option = quote.options.firstOrNull { it.serviceLevel.equals(row.serviceLevel.name, ignoreCase = true) }
            ?: throw InvalidShipmentRequestException(
                "service_level_unavailable",
                "The ${row.serviceLevel.name} service is not offered between these two places.",
            )
        val result = createShipment.create(
            actorUserId,
            idempotencyKey,
            CreateShipmentRequest(
                quoteId = option.quoteId,
                merchantReference = row.merchantReference?.trim()?.takeIf { it.isNotEmpty() },
                pickupAddressId = row.pickupAddressId,
                recipient = row.recipient,
                shippingFeePayer = row.shippingFeePayer,
                parcels = listOf(
                    ParcelContentsRequest(
                        description = row.parcel.description,
                        quantity = row.parcel.quantity,
                        category = row.parcel.category,
                        isFragile = row.parcel.isFragile,
                    ),
                ),
                note = row.note?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        return created(row, result.shipment.trackingCode, result.shipment.pricing.shippingFee, result.replayed)
    }

    private fun created(row: BulkShipmentRowRequest, trackingCode: String, fee: Long, replayed: Boolean) =
        BulkShipmentRowResult(row.rowNumber, BulkShipmentRowStatus.CREATED, trackingCode, fee, replayed)

    private fun failed(row: BulkShipmentRowRequest, code: String, message: String) =
        BulkShipmentRowResult(row.rowNumber, BulkShipmentRowStatus.FAILED, code = code, message = message)

    companion object {
        private val log = LoggerFactory.getLogger(BulkCreateShipmentService::class.java)

        /** Stable per spreadsheet row, within the 8..100 safe characters [CreateShipmentService] accepts. */
        fun idempotencyKey(batchId: UUID, rowNumber: Int) = "bulk-$batchId-$rowNumber"
    }
}
