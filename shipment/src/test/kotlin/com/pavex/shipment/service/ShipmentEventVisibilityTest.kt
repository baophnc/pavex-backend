package com.pavex.shipment.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShipmentEventVisibilityTest {
    @Test
    fun `customer timeline hides internal workflow events`() {
        assertTrue(ShipmentEventVisibility.isCustomerVisible("shipment.created"))
        assertTrue(ShipmentEventVisibility.isCustomerVisible("shipment.cancelled"))
        assertFalse(ShipmentEventVisibility.isCustomerVisible("operations.assignment.created"))
        assertFalse(ShipmentEventVisibility.isCustomerVisible("shipment.weight-review.requested"))
    }
}
