package com.pavex.shipment.service

import com.pavex.contracts.partners.MerchantOrderCapabilitySnapshot
import com.pavex.contracts.partners.MerchantPickupAddressSnapshot
import com.pavex.contracts.pricing.PricingQuoteCoverageTier
import com.pavex.contracts.pricing.PricingQuoteFeeSnapshot
import com.pavex.contracts.pricing.PricingQuoteLocationSnapshot
import com.pavex.contracts.pricing.PricingQuoteParcelSnapshot
import com.pavex.contracts.pricing.PricingQuoteServiceLevel
import com.pavex.contracts.pricing.PricingQuoteSnapshot
import com.pavex.contracts.pricing.PricingQuoteZone
import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.client.PartnerShipmentClient
import com.pavex.shipment.client.PricingQuoteClient
import com.pavex.shipment.dto.CreateShipmentRequest
import com.pavex.shipment.dto.ParcelContentsRequest
import com.pavex.shipment.dto.ShipmentAddressRequest
import com.pavex.shipment.entity.GoodsCategory
import com.pavex.shipment.entity.ShippingFeePayer
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentConflictException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** The sender of a shipment always comes from one of the merchant's own pickup addresses. */
class CreateShipmentPickupSenderTest {
    private val actor = UUID.randomUUID()
    private val merchantId = UUID.randomUUID()
    private val pickupId = UUID.randomUUID()
    private val networkCalls = mutableListOf<String>()
    private var pickup: MerchantPickupAddressSnapshot? = null
    private var requestedMerchant: UUID? = null
    private var shipmentUsingReference: String? = null

    private val service = CreateShipmentService(
        partners = mock(PartnerShipmentClient::class.java) { invocation ->
            when (invocation.method.name) {
                "orderCapability" -> MerchantOrderCapabilitySnapshot(merchantId, "M001", actor, "ACTIVE", true, pickupId)
                "pickupAddress" -> pickup.also { requestedMerchant = invocation.getArgument(0) }
                else -> null
            }
        },
        pricing = mock(PricingQuoteClient::class.java) { invocation ->
            if (invocation.method.name == "getValid") quote(invocation.getArgument(0)) else null
        },
        network = mock(NetworkShipmentClient::class.java) { invocation -> null.also { networkCalls += invocation.method.name } },
        operations = mock(OperationsShipmentClient::class.java),
        networkConfirmations = mock(ShipmentNetworkConfirmationService::class.java),
        writer = mock(ShipmentCreationWriter::class.java) { invocation ->
            if (invocation.method.name == "shipmentWithMerchantReference") shipmentUsingReference else null
        },
        trackingCodes = mock(ShipmentCodeGenerator::class.java),
        objectMapper = JsonMapper.builder().build(),
    )

    @Test
    fun `a pickup address outside the merchant is refused before any reservation`() {
        pickup = null

        val error = assertThrows(InvalidShipmentRequestException::class.java) { create() }

        assertEquals("pickup_address_not_found", error.code)
        assertEquals(merchantId, requestedMerchant)
        assertEquals(emptyList<String>(), networkCalls)
    }

    @Test
    fun `a pickup address that cannot be a sender is reported as such`() {
        pickup = pickupAt("48", "20410", phone = "028 3823 4567")

        val error = assertThrows(InvalidShipmentRequestException::class.java) { create() }

        assertEquals("pickup_address_invalid", error.code)
        assertEquals(emptyList<String>(), networkCalls)
    }

    @Test
    fun `the quote must have been priced from the pickup address`() {
        pickup = pickupAt("01", "00004", phone = "0912345678")

        val error = assertThrows(InvalidShipmentRequestException::class.java) { create() }

        assertEquals("quote_location_mismatch", error.code)
        assertEquals(emptyList<String>(), networkCalls)
    }

    @Test
    fun `a merchant reference already used by a live shipment is refused`() {
        pickup = pickupAt("48", "20410", phone = "0912345678")
        shipmentUsingReference = "PAVEX2609250123456789"

        val error = assertThrows(ShipmentConflictException::class.java) { create(merchantReference = " ORDER-1001 ") }

        assertEquals("merchant_reference_duplicate", error.code)
        assertEquals(emptyList<String>(), networkCalls)
    }

    private fun create(merchantReference: String? = null) = service.create(
        actor,
        "create-key-0001",
        CreateShipmentRequest(
            quoteId = UUID.randomUUID(),
            merchantReference = merchantReference,
            pickupAddressId = pickupId,
            recipient = ShipmentAddressRequest("Người nhận", "0987654321", "12 Lê Lợi", "79", "26740"),
            shippingFeePayer = ShippingFeePayer.SENDER,
            parcels = listOf(ParcelContentsRequest("Áo thun", 1, GoodsCategory.APPAREL)),
        ),
    )

    private fun pickupAt(provinceCode: String, wardCode: String, phone: String) = MerchantPickupAddressSnapshot(
        id = pickupId,
        merchantId = merchantId,
        name = "Kho chính",
        contactName = "Người gửi",
        contactPhone = phone,
        addressLine = "1 Trần Phú",
        provinceCode = provinceCode,
        wardCode = wardCode,
    )

    /** A quote priced from province 48 / ward 20410 to the recipient's ward. */
    private fun quote(quoteId: UUID): PricingQuoteSnapshot {
        val now = Instant.now()
        fun location(province: String, ward: String) = PricingQuoteLocationSnapshot(
            province, ward, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PricingQuoteCoverageTier.STANDARD,
        )
        val one = BigDecimal("1.00")
        return PricingQuoteSnapshot(
            quoteId = quoteId,
            requestId = UUID.randomUUID(),
            ratePlanId = UUID.randomUUID(),
            rateCode = "RATE",
            rateRevision = 1,
            rateVersion = "RATE-r1",
            currency = "VND",
            serviceLevel = PricingQuoteServiceLevel.STANDARD,
            pricingZone = PricingQuoteZone.CROSS_REGION,
            origin = location("48", "20410"),
            destination = location("79", "26740"),
            actualWeightKg = one,
            volumetricWeightKg = one,
            chargeableWeightKg = one,
            parcelCount = 1,
            parcels = listOf(PricingQuoteParcelSnapshot(1, one, one, one, BigDecimal("20"), BigDecimal("20"), BigDecimal("20"))),
            codAmount = 0,
            declaredValue = 0,
            fees = PricingQuoteFeeSnapshot(20_000, 0, 0, 0, 0, 20_000),
            estimatedMinDays = 1,
            estimatedMaxDays = 2,
            createdAt = now,
            expiresAt = now.plusSeconds(900),
        )
    }
}
