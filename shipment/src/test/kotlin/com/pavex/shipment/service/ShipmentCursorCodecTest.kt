package com.pavex.shipment.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ShipmentCursorCodecTest {
    @Test
    fun `cursor round trips without exposing raw paging fields`() {
        val createdAt = Instant.parse("2026-08-29T10:15:30Z")
        val code = "PAVEX2608290123456789"

        val encoded = ShipmentCursorCodec.encode(createdAt, code)
        val decoded = ShipmentCursorCodec.decodeOrNull(encoded)

        assertEquals(createdAt, decoded?.createdAt)
        assertEquals(code, decoded?.trackingCode)
    }

    @Test
    fun `invalid cursor is rejected`() {
        assertNull(ShipmentCursorCodec.decodeOrNull("not-a-valid-cursor"))
    }
}
