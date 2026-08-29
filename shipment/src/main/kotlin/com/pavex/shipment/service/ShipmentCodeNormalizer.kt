package com.pavex.shipment.service

object ShipmentCodeNormalizer {
    private val pattern = Regex("^PAVEX[0-9]{6}[0-9ABCDEFGHJKMNPQRSTVWXYZ]{10}(?:-[0-9]{2})?$")

    fun normalizeOrNull(raw: String?): String? {
        val normalized = raw?.trim()?.uppercase().orEmpty()
        return normalized.takeIf(pattern::matches)
    }
}
