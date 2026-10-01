package com.pavex.shipment.service

import com.pavex.common.security.HubAccessScope
import com.pavex.contracts.partners.MerchantPickupAddressSnapshot
import com.pavex.shipment.client.PartnerShipmentClient
import com.pavex.shipment.dto.AdminUpdateShipmentRequest
import com.pavex.shipment.dto.UpdateShipmentRequest
import com.pavex.shipment.entity.GoodsCategory
import com.pavex.shipment.entity.ParcelDraft
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentAddressSnapshot
import com.pavex.shipment.entity.ShipmentCoverageTier
import com.pavex.shipment.entity.ShipmentNetworkPointSnapshot
import com.pavex.shipment.entity.ShipmentPriceSnapshot
import com.pavex.shipment.entity.ShipmentPricingZone
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShippingFeePayer
import com.pavex.shipment.exception.ShipmentApiException
import com.pavex.shipment.repository.ShipmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** A shop edits its shipment before pickup; staff correct the recipient afterwards, with a reason. */
class UpdateShipmentServiceTest {
    private val now = Instant.parse("2026-10-01T08:00:00Z")
    private val shop = UUID.randomUUID()
    private val staff = UUID.randomUUID()
    private val shipment = shipment()
    private val otherShipmentCodes = mutableListOf<String>()
    private val pickups = mutableMapOf<UUID, MerchantPickupAddressSnapshot>()

    private val shipments = mock(ShipmentRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "findByTrackingCodeIgnoreCaseAndCreatedByUserId" ->
                shipment.takeIf { invocation.getArgument<UUID>(1) == shop }
            "findActiveTrackingCodesByMerchantReference" -> otherShipmentCodes + shipment.trackingCode
            "saveAndFlush" -> invocation.getArgument(0)
            else -> RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val partners = mock(PartnerShipmentClient::class.java) { invocation ->
        if (invocation.method.name == "pickupAddress") pickups[invocation.getArgument(1)] else RETURNS_DEFAULTS.answer(invocation)
    }
    private val adminQueries = mock(AdminShipmentQueryService::class.java) { invocation ->
        if (invocation.method.name == "findInScope") shipment else RETURNS_DEFAULTS.answer(invocation)
    }
    private val service = UpdateShipmentService(shipments, partners, adminQueries, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `a shop's edit is written on the timeline once`() {
        val request = UpdateShipmentRequest(recipientPhone = "0987 654 321", note = "Gọi trước khi giao")

        service.updateOwn(shop, shipment.trackingCode, request)
        service.updateOwn(shop, shipment.trackingCode, request)

        assertEquals("0987654321", shipment.recipient.contactPhone)
        assertEquals("Gọi trước khi giao", shipment.note)
        val edits = shipment.events.filter { it.code == "shipment.details-updated" }
        assertEquals(listOf("Cập nhật số điện thoại người nhận, ghi chú"), edits.map { it.description })
        assertEquals(shop, edits.single().actorUserId)
    }

    @Test
    fun `after pickup only staff can change the recipient, and say why`() {
        shipment.transitionParcel(
            parcelId = shipment.parcels.single().id,
            nextStatus = ParcelStatus.PICKED_UP,
            eventCode = "parcel.picked-up",
            description = "scan",
            actorUserId = staff,
            hubId = shipment.originHubId,
            locationName = "Origin",
            occurredAt = now,
            recordedAt = now,
        )

        val refused = assertThrows(ShipmentApiException::class.java) {
            service.updateOwn(shop, shipment.trackingCode, UpdateShipmentRequest(recipientName = "C"))
        }
        assertEquals("shipment_already_picked_up", refused.code)

        service.updateAsStaff(
            HubAccessScope.global(staff),
            staff,
            shipment.trackingCode,
            AdminUpdateShipmentRequest(recipientAddressLine = "Số 5 ngõ 10", reason = "Khách gọi tổng đài"),
        )
        assertEquals("Số 5 ngõ 10", shipment.recipient.addressLine)
        assertEquals(
            "Cập nhật địa chỉ người nhận. Lý do: Khách gọi tổng đài",
            shipment.events.last { it.code == "shipment.details-updated" }.description,
        )
    }

    @Test
    fun `another pickup point must stay in the same ward`() {
        val sameWard = pickup(wardCode = shipment.sender.wardCode)
        val otherWard = pickup(wardCode = "20413")

        val moved = assertThrows(ShipmentApiException::class.java) {
            service.updateOwn(shop, shipment.trackingCode, UpdateShipmentRequest(pickupAddressId = otherWard.id))
        }
        assertEquals("pickup_address_area_changed", moved.code)

        service.updateOwn(shop, shipment.trackingCode, UpdateShipmentRequest(pickupAddressId = sameWard.id))
        assertEquals(sameWard.id, shipment.pickupAddressId)
        assertEquals("Kho mới", shipment.sender.contactName)
        assertEquals(shipment.sender.wardName, "Hội An")
    }

    @Test
    fun `a shop reference used by another live shipment is refused`() {
        otherShipmentCodes += "PAVEX2610010000000001"

        val duplicate = assertThrows(ShipmentApiException::class.java) {
            service.updateOwn(shop, shipment.trackingCode, UpdateShipmentRequest(merchantReference = "DH-01"))
        }
        assertEquals("merchant_reference_duplicate", duplicate.code)
    }

    private fun pickup(wardCode: String) = MerchantPickupAddressSnapshot(
        id = UUID.randomUUID(),
        merchantId = shipment.merchantId,
        name = "Kho",
        contactName = "Kho mới",
        contactPhone = "0901111222",
        addressLine = "12 Trần Phú",
        provinceCode = shipment.sender.provinceCode,
        wardCode = wardCode,
    ).also { pickups[it.id] = it }

    private fun shipment(): Shipment = Shipment.createFoundation(
        id = UUID.randomUUID(),
        trackingCode = "PAVEX2610016VFPA4QE4Z",
        merchantId = UUID.randomUUID(),
        createdByUserId = shop,
        idempotencyKey = "shipment-edit-001",
        requestFingerprint = "C".repeat(64),
        merchantReference = null,
        pickupAddressId = UUID.randomUUID(),
        sender = ShipmentAddressSnapshot.create("A", "0901234567", "A", "48", "Đà Nẵng", "20410", "Hội An"),
        recipient = ShipmentAddressSnapshot.create("B", "0912345678", "B", "79", "Hồ Chí Minh", "26740", "Sài Gòn"),
        shippingFeePayer = ShippingFeePayer.SENDER,
        price = ShipmentPriceSnapshot(
            quoteId = UUID.randomUUID(),
            quoteRequestId = UUID.randomUUID(),
            ratePlanId = UUID.randomUUID(),
            rateCode = "RATE",
            rateRevision = 1,
            rateVersion = "RATE-r1",
            currency = "VND",
            serviceLevel = ShipmentServiceLevel.STANDARD,
            pricingZone = ShipmentPricingZone.CROSS_REGION,
            baseFee = 10_000,
            additionalWeightFee = 0,
            areaSurcharge = 0,
            codFee = 0,
            insuranceFee = 0,
            shippingFee = 10_000,
            codAmount = 0,
            declaredValue = 0,
            estimatedMinDays = 1,
            estimatedMaxDays = 2,
            quoteExpiresAt = now.plusSeconds(1800),
        ),
        origin = ShipmentNetworkPointSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ORI", "Origin", ShipmentCoverageTier.STANDARD),
        destination = ShipmentNetworkPointSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DST", "Destination", ShipmentCoverageTier.STANDARD),
        parcelDrafts = listOf(
            ParcelDraft(
                description = "Box",
                quantity = 1,
                category = GoodsCategory.OTHER,
                isFragile = false,
                actualWeightKg = BigDecimal("1.000"),
                volumetricWeightKg = BigDecimal("0.500"),
                chargeableWeightKg = BigDecimal("1.000"),
                lengthCm = BigDecimal("20.00"),
                widthCm = BigDecimal("20.00"),
                heightCm = BigDecimal("20.00"),
            ),
        ),
        note = null,
        createdAt = now,
    )
}
