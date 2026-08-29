package com.pavex.shipment.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ShipmentCodeGeneratorTest {
    private val generator = ShipmentCodeGenerator()

    @Test
    fun `shipment code is human facing and does not expose uuid`() {
        val code = generator.create(Instant.parse("2026-08-29T00:00:00Z"))

        assertTrue(code.matches(Regex("^PAVEX260829[0-9ABCDEFGHJKMNPQRSTVWXYZ]{10}$")))
    }
}
