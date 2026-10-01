package com.pavex.shipment.service

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
import com.pavex.shipment.dto.CreateCounterShipmentRequest
import com.pavex.shipment.dto.ParcelContentsRequest
import com.pavex.shipment.dto.ShipmentAddressRequest
import com.pavex.shipment.entity.GoodsCategory
import com.pavex.shipment.entity.ShippingFeePayer
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Hub staff booking a walk-in customer's shipment at the counter of their own hub. */
class CreateCounterShipmentTest {
    private val clerk = UUID.randomUUID()
    private val counterHub = UUID.randomUUID()
    private val networkCalls = mutableListOf<String>()
    private var servesCounter = true
    private var quoteOriginHub = counterHub
    private val partnerCalls = mutableListOf<String>()

    private val service = CreateShipmentService(
        partners = mock(PartnerShipmentClient::class.java) { invocation -> null.also { partnerCalls += invocation.method.name } },
        pricing = mock(PricingQuoteClient::class.java) { invocation ->
            if (invocation.method.name == "getValid") quote(invocation.getArgument(0)) else null
        },
        network = mock(NetworkShipmentClient::class.java) { invocation -> null.also { networkCalls += invocation.method.name } },
        operations = mock(OperationsShipmentClient::class.java) { invocation ->
            if (invocation.method.name == "canOperateAtHub") servesCounter && invocation.getArgument<UUID>(1) == counterHub else null
        },
        networkConfirmations = mock(ShipmentNetworkConfirmationService::class.java),
        writer = mock(ShipmentCreationWriter::class.java),
        trackingCodes = mock(ShipmentCodeGenerator::class.java),
        objectMapper = JsonMapper.builder().build(),
    )

    @Test
    fun `only staff serving that hub can book at its counter`() {
        servesCounter = false

        val error = assertThrows(ShipmentForbiddenException::class.java) { create() }

        assertEquals("counter_hub_forbidden", error.code)
        assertEquals(emptyList<String>(), networkCalls)
    }

    @Test
    fun `a walk-in sender needs no shop or pickup address`() {
        quoteOriginHub = UUID.randomUUID()

        assertThrows(InvalidShipmentRequestException::class.java) { create() }

        assertEquals(emptyList<String>(), partnerCalls)
    }

    @Test
    fun `the quote must start at the counter's hub`() {
        quoteOriginHub = UUID.randomUUID()

        val error = assertThrows(InvalidShipmentRequestException::class.java) { create() }

        assertEquals("counter_quote_hub_mismatch", error.code)
        assertEquals(emptyList<String>(), networkCalls)
    }

    @Test
    fun `the cash taken must be the whole fee when the sender pays`() {
        val error = assertThrows(InvalidShipmentRequestException::class.java) {
            create(payer = ShippingFeePayer.SENDER, cashReceived = 20_000)
        }

        assertEquals("counter_cash_mismatch", error.code)
        assertEquals(emptyList<String>(), networkCalls)
    }

    @Test
    fun `no cash is taken when the recipient pays on delivery`() {
        val error = assertThrows(InvalidShipmentRequestException::class.java) {
            create(payer = ShippingFeePayer.RECIPIENT, cashReceived = 22_000)
        }

        assertEquals("counter_cash_mismatch", error.code)
        assertEquals(emptyList<String>(), networkCalls)
    }

    @Test
    fun `the recipient must be where the quote delivers`() {
        val error = assertThrows(InvalidShipmentRequestException::class.java) {
            create(recipientWard = "00004")
        }

        assertEquals("quote_location_mismatch", error.code)
    }

    private fun create(
        payer: ShippingFeePayer = ShippingFeePayer.SENDER,
        cashReceived: Long = 22_000,
        recipientWard: String = "26740",
    ) = service.createAtCounter(
        clerk,
        "counter-key-0001",
        CreateCounterShipmentRequest(
            hubId = counterHub,
            quoteId = UUID.randomUUID(),
            sender = ShipmentAddressRequest("Khách lẻ", "0901234567", "5 Nguyễn Huệ", "01", "00004"),
            recipient = ShipmentAddressRequest("Người nhận", "0987654321", "12 Lê Lợi", "79", recipientWard),
            shippingFeePayer = payer,
            parcels = listOf(ParcelContentsRequest("Áo thun", 1, GoodsCategory.APPAREL)),
            cashReceived = cashReceived,
        ),
    )

    /** A 22,000 VND quote priced from the hub's own ward (48 / 20410) to the recipient's ward. */
    private fun quote(quoteId: UUID): PricingQuoteSnapshot {
        val now = Instant.now()
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
            origin = PricingQuoteLocationSnapshot(
                "48", "20410", UUID.randomUUID(), UUID.randomUUID(), quoteOriginHub, PricingQuoteCoverageTier.STANDARD,
            ),
            destination = PricingQuoteLocationSnapshot(
                "79", "26740", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PricingQuoteCoverageTier.STANDARD,
            ),
            actualWeightKg = one,
            volumetricWeightKg = one,
            chargeableWeightKg = one,
            parcelCount = 1,
            parcels = listOf(PricingQuoteParcelSnapshot(1, one, one, one, BigDecimal("20"), BigDecimal("20"), BigDecimal("20"))),
            codAmount = 0,
            declaredValue = 0,
            fees = PricingQuoteFeeSnapshot(20_000, 0, 0, 2_000, 0, 22_000),
            estimatedMinDays = 1,
            estimatedMaxDays = 2,
            createdAt = now,
            expiresAt = now.plusSeconds(900),
        )
    }
}
