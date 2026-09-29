package com.pavex.shipment.service

import com.pavex.shipment.entity.ShipmentFixtures
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.entity.ShipmentOutboxEvent
import com.pavex.shipment.entity.ShippingFeePayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class ShipmentFinancialEventsTest {
    private val mapper = JsonMapper.builder().build()
    private val enqueued = mutableListOf<Pair<String, String>>()
    private val outbox = mock(ShipmentOutboxService::class.java) { invocation ->
        if (invocation.method.name == "enqueue") {
            enqueued += invocation.getArgument<String>(2) to invocation.getArgument<String>(3)
            ShipmentOutboxEvent()
        } else {
            null
        }
    }
    private val events = ShipmentFinancialEvents(outbox, mapper)

    @Test
    fun `closing a shipment publishes its money snapshot once`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 250_000)
        shipment.cancel("Merchant cancelled", UUID.randomUUID(), shipment.createdAt.plusSeconds(60))

        events.closedIfChanged(shipment, ShipmentLifecycleStatus.ACTIVE)
        events.closedIfChanged(shipment, ShipmentLifecycleStatus.CANCELLED)

        val (type, payload) = enqueued.single()
        assertEquals(ShipmentFinancialEvents.CLOSED, type)
        val json = mapper.readTree(payload)
        assertEquals("CANCELLED", json["lifecycleStatus"].asString())
        assertEquals(0, json["merchantShippingFee"].asLong())
        assertEquals(shipment.merchantId.toString(), json["merchantId"].asString())
    }

    @Test
    fun `an active shipment is not reported as closed`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.SENDER, codAmount = 0)

        events.closedIfChanged(shipment, ShipmentLifecycleStatus.ACTIVE)

        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun `cash collection reports the amount and its fee part`() {
        val shipment = ShipmentFixtures.moneyShipment(ShippingFeePayer.RECIPIENT, codAmount = 250_000)
        val courier = UUID.randomUUID()
        val at = shipment.createdAt.plusSeconds(60)
        shipment.recordCollection(272_000, courier, UUID.randomUUID(), shipment.parcels.single().id, shipment.destinationHubId, "", at, at)

        events.cashCollected(shipment)

        val (type, payload) = enqueued.single()
        assertEquals(ShipmentFinancialEvents.CASH_COLLECTED, type)
        val json = mapper.readTree(payload)
        assertEquals(272_000, json["amount"].asLong())
        assertEquals(22_000, json["recipientShippingFee"].asLong())
        assertEquals(courier.toString(), json["courierUserId"].asString())
    }
}
