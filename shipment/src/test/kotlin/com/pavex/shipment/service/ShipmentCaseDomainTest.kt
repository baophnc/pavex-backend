package com.pavex.shipment.service

import com.pavex.shipment.entity.ShipmentCase
import com.pavex.shipment.entity.ShipmentCaseSeverity
import com.pavex.shipment.entity.ShipmentCaseStatus
import com.pavex.shipment.entity.ShipmentCaseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ShipmentCaseDomainTest {
    private val actor = UUID.randomUUID()
    private val hub = UUID.randomUUID()
    private val shipment = UUID.randomUUID()
    private val now = Instant.parse("2026-08-31T10:00:00Z")

    @Test
    fun `problem parcel case follows assignment investigation resolution and close lifecycle`() {
        val item = ShipmentCase.createProblem(
            shipment,
            UUID.randomUUID(),
            hub,
            ShipmentCaseSeverity.HIGH,
            "Damaged parcel",
            "Outer packaging is torn.",
            actor,
            now,
        )
        val assignee = UUID.randomUUID()
        item.assign(assignee, actor, null, now.plusSeconds(60))
        item.startInvestigation(actor, "Inspect CCTV and scan history.", now.plusSeconds(120))
        item.addNote(actor, "Damage confirmed at inbound dock.", now.plusSeconds(180))
        item.resolve(actor, "Repacked parcel and recorded damage evidence.", now.plusSeconds(240))
        item.close(actor, null, now.plusSeconds(300))

        assertEquals(ShipmentCaseType.PROBLEM_PARCEL, item.type)
        assertEquals(ShipmentCaseStatus.CLOSED, item.status)
        assertEquals(assignee, item.assignedToUserId)
        assertEquals(6, item.events.size)
        assertEquals("case.closed", item.events.last().code)
    }

    @Test
    fun `unidentified case can link once to a shipment`() {
        val item = ShipmentCase.createUnidentified(
            hub,
            ShipmentCaseSeverity.MEDIUM,
            "Unknown parcel",
            "Parcel has no Pavex shipment label.",
            "EXT-12345",
            actor,
            now,
        )
        val parcel = UUID.randomUUID()
        item.linkToShipment(shipment, parcel, actor, "Matched from merchant manifest.", now.plusSeconds(60))

        assertEquals(shipment, item.shipmentId)
        assertEquals(parcel, item.parcelId)
        assertTrue(item.events.any { it.code == "case.shipment-linked" })
        assertThrows(IllegalStateException::class.java) {
            item.linkToShipment(UUID.randomUUID(), null, actor, null, now.plusSeconds(120))
        }
    }

    @Test
    fun `resolved case cannot be reassigned and only resolved case can close`() {
        val item = ShipmentCase.createProblem(
            shipment, null, hub, ShipmentCaseSeverity.LOW, "Label issue", "Label is unreadable.", actor, now,
        )
        assertThrows(IllegalStateException::class.java) { item.close(actor, null, now.plusSeconds(10)) }
        item.resolve(actor, "Reprinted label.", now.plusSeconds(20))
        assertThrows(IllegalStateException::class.java) {
            item.assign(UUID.randomUUID(), actor, null, now.plusSeconds(30))
        }
    }
}
