package com.pavex.shipment.service

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

@Component
class ShipmentCodeGenerator {
    private val secureRandom = SecureRandom()
    fun create(timestamp: Instant): String {
        val date = timestamp.atZone(ZoneOffset.UTC).toLocalDate()
        val suffix = buildString(RANDOM_LENGTH) {
            repeat(RANDOM_LENGTH) {
                append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
            }
        }
        return String.format(
            Locale.ROOT,
            "PAVEX%02d%02d%02d%s",
            date.year % 100,
            date.monthValue,
            date.dayOfMonth,
            suffix,
        )
    }

    companion object {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val RANDOM_LENGTH = 10
    }
}
