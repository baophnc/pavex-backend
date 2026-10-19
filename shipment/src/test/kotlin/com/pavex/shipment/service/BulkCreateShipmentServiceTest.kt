package com.pavex.shipment.service

import com.pavex.contracts.partners.MerchantOrderCapabilitySnapshot
import com.pavex.contracts.partners.MerchantPickupAddressSnapshot
import com.pavex.shipment.client.PartnerShipmentClient
import com.pavex.shipment.client.PricingQuoteClient
import com.pavex.shipment.client.PricingQuoteOption
import com.pavex.shipment.client.PricingQuoteRequest
import com.pavex.shipment.client.PricingQuoteResult
import com.pavex.shipment.dto.BulkCreateShipmentsRequest
import com.pavex.shipment.dto.BulkParcelRequest
import com.pavex.shipment.dto.BulkShipmentRowRequest
import com.pavex.shipment.dto.BulkShipmentRowStatus
import com.pavex.shipment.dto.CreateShipmentRequest
import com.pavex.shipment.dto.ShipmentAddressRequest
import com.pavex.shipment.entity.GoodsCategory
import com.pavex.shipment.entity.ShipmentFixtures
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShippingFeePayer
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.util.UUID

/** A spreadsheet slice books each row like the shop's form would, and one bad row never stops the others. */
class BulkCreateShipmentServiceTest {
    private val actor = UUID.randomUUID()
    private val merchantId = UUID.randomUUID()
    private val pickupId = UUID.randomUUID()
    private val batchId = UUID.randomUUID()
    private val standardQuote = UUID.randomUUID()
    private val expressQuote = UUID.randomUUID()

    private var canCreateOrders = true
    private var pickup: MerchantPickupAddressSnapshot? = MerchantPickupAddressSnapshot(
        pickupId, merchantId, "Kho chính", "Người gửi", "0912345678", "1 Trần Phú", "48", "20410",
    )
    private var pickupLookups = 0
    private val quoted = mutableListOf<PricingQuoteRequest>()
    private var refuseDestinationWard: String? = null
    private var offered = listOf(ShipmentServiceLevel.STANDARD, ShipmentServiceLevel.EXPRESS)
    private val booked = mutableListOf<Pair<String, CreateShipmentRequest>>()
    private val alreadyCreated = mutableSetOf<String>()
    private val shipment = ShipmentResponseMapper.created(ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, 0))

    private val service = BulkCreateShipmentService(
        partners = mock(PartnerShipmentClient::class.java) { invocation ->
            when (invocation.method.name) {
                "orderCapability" -> MerchantOrderCapabilitySnapshot(merchantId, "M001", actor, "ACTIVE", canCreateOrders, pickupId)
                "pickupAddress" -> pickup.also { pickupLookups++ }
                else -> null
            }
        },
        pricing = mock(PricingQuoteClient::class.java) { invocation ->
            val request = invocation.getArgument<PricingQuoteRequest>(0)
            quoted += request
            if (request.destinationWardCode == refuseDestinationWard) {
                throw InvalidShipmentRequestException("location_not_served", "Pricing does not serve this ward.")
            }
            PricingQuoteResult(offered.map {
                PricingQuoteOption(if (it == ShipmentServiceLevel.STANDARD) standardQuote else expressQuote, it.name, 30_000)
            })
        },
        createShipment = mock(CreateShipmentService::class.java) { invocation ->
            booked += invocation.getArgument<String>(1) to invocation.getArgument<CreateShipmentRequest>(2)
            CreateShipmentResult(shipment, replayed = false)
        },
        writer = mock(ShipmentCreationWriter::class.java) { invocation ->
            val key = invocation.getArgument<String>(1)
            if (invocation.method.name == "findExisting" && key in alreadyCreated) ExistingShipmentCreation("F", shipment) else null
        },
    )

    @Test
    fun `each row is priced from the pickup address with its own service level and booked under its own key`() {
        val result = service.create(actor, BulkCreateShipmentsRequest(batchId, listOf(
            row(2, level = ShipmentServiceLevel.STANDARD, reference = " SHOP-1 ", note = "  "),
            row(3, level = ShipmentServiceLevel.EXPRESS),
        )))

        assertEquals(2, result.created)
        assertEquals(0, result.failed)
        assertEquals(listOf("48" to "20410"), quoted.map { it.originProvinceCode to it.originWardCode }.distinct())
        assertEquals(listOf(standardQuote, expressQuote), booked.map { it.second.quoteId })
        assertEquals(listOf("bulk-$batchId-2", "bulk-$batchId-3"), booked.map { it.first })
        assertEquals("SHOP-1", booked[0].second.merchantReference)
        assertNull(booked[0].second.note)
        assertEquals(shipment.trackingCode, result.results[0].trackingCode)
        assertEquals(shipment.pricing.shippingFee, result.results[0].shippingFee)
        assertEquals(1, pickupLookups, "one lookup per pickup address, not per row")
    }

    @Test
    fun `a row refused by pricing fails alone`() {
        refuseDestinationWard = "99999"

        val result = service.create(actor, BulkCreateShipmentsRequest(batchId, listOf(row(2, ward = "99999"), row(3))))

        assertEquals(listOf(BulkShipmentRowStatus.FAILED, BulkShipmentRowStatus.CREATED), result.results.map { it.status })
        assertEquals("location_not_served", result.results[0].code)
        assertEquals(1, booked.size)
    }

    @Test
    fun `a row created by an earlier send is returned without pricing it again`() {
        alreadyCreated += "bulk-$batchId-2"

        val result = service.create(actor, BulkCreateShipmentsRequest(batchId, listOf(row(2))))

        assertEquals(BulkShipmentRowStatus.CREATED, result.results.single().status)
        assertTrue(result.results.single().replayed)
        assertEquals(emptyList<PricingQuoteRequest>(), quoted)
        assertEquals(emptyList<Pair<String, CreateShipmentRequest>>(), booked)
    }

    @Test
    fun `an unknown pickup address and an unoffered service level are reported per row`() {
        offered = listOf(ShipmentServiceLevel.STANDARD)

        val unoffered = service.create(actor, BulkCreateShipmentsRequest(batchId, listOf(row(2, level = ShipmentServiceLevel.EXPRESS))))
        pickup = null
        val unknownPickup = service.create(actor, BulkCreateShipmentsRequest(batchId, listOf(row(3))))

        assertEquals("service_level_unavailable", unoffered.results.single().code)
        assertEquals("pickup_address_not_found", unknownPickup.results.single().code)
        assertEquals(emptyList<Pair<String, CreateShipmentRequest>>(), booked)
    }

    @Test
    fun `a client key from the row's content keys the order, so the same row uploaded again is not booked twice`() {
        alreadyCreated += "bulk-row-3f9a2c41d07e"

        val result = service.create(actor, BulkCreateShipmentsRequest(UUID.randomUUID(), listOf(
            row(2).copy(clientKey = "row-3f9a2c41d07e"),
            row(3).copy(clientKey = "row-77b0e15c9a42"),
        )))

        assertEquals(listOf(true, false), result.results.map { it.replayed })
        assertEquals(listOf("bulk-row-77b0e15c9a42"), booked.map { it.first })
    }

    @Test
    fun `a shop that cannot order yet is refused before any row`() {
        canCreateOrders = false

        val error = assertThrows(ShipmentForbiddenException::class.java) {
            service.create(actor, BulkCreateShipmentsRequest(batchId, listOf(row(2))))
        }

        assertEquals("merchant_cannot_create_orders", error.code)
        assertEquals(emptyList<PricingQuoteRequest>(), quoted)
    }

    private fun row(
        number: Int,
        ward: String = "26740",
        level: ShipmentServiceLevel = ShipmentServiceLevel.STANDARD,
        reference: String? = null,
        note: String? = null,
    ) = BulkShipmentRowRequest(
        rowNumber = number,
        merchantReference = reference,
        pickupAddressId = pickupId,
        recipient = ShipmentAddressRequest("Người nhận", "0987654321", "12 Lê Lợi", "79", ward),
        parcel = BulkParcelRequest("Áo thun", 1, GoodsCategory.APPAREL, false, BigDecimal("0.5"), BigDecimal("30"),
            BigDecimal("20"), BigDecimal("5")),
        declaredValue = 200_000,
        codAmount = 200_000,
        serviceLevel = level,
        shippingFeePayer = ShippingFeePayer.RECIPIENT,
        note = note,
    )
}
