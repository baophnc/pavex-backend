package com.pavex.shipment.service

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

object ShipmentCursorCodec {
    data class Cursor(val createdAt: Instant, val trackingCode: String)

    fun encode(createdAt: Instant, trackingCode: String): String {
        val payload = "${createdAt.toEpochMilli()}|${trackingCode.trim().uppercase()}"
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
    }

    fun decodeOrNull(value: String?): Cursor? {
        if (value.isNullOrBlank()) return null
        return try {
            val decoded = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
            val parts = decoded.split('|', limit = 2)
            if (parts.size != 2) return null
            val timestamp = parts[0].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
            val trackingCode = ShipmentCodeNormalizer.normalizeOrNull(parts[1]) ?: return null
            if (trackingCode.contains('-')) return null
            Cursor(timestamp, trackingCode)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
