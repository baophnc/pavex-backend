package com.pavex.shipment.service

object ShipmentEventVisibility {
    private val internalPrefixes = listOf(
        "operations.assignment.",
        "shipment.hold.",
        "shipment.return.",
        "shipment.forward.",
        "shipment.cancellation.",
        "shipment.weight-",
    )

    fun isCustomerVisible(code: String): Boolean =
        internalPrefixes.none { prefix -> code.startsWith(prefix) }
}
