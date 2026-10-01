package com.pavex.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ObjectKeysTest {
    @Test
    fun `keys are grouped by kind and UTC month`() {
        val id = UUID.fromString("1b4e28ba-2fa1-11d2-883f-0016d3cca427")

        assertEquals(
            "evidence/2026/01/1b4e28ba-2fa1-11d2-883f-0016d3cca427",
            ObjectKeys.dated("evidence", id, Instant.parse("2026-01-31T23:30:00Z")),
        )
    }
}
