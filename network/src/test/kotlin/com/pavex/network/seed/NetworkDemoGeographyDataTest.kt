package com.pavex.network.seed

import com.pavex.network.entity.CoverageTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkDemoGeographyDataTest {
    @Test
    fun `demo geography covers all target commune-level units exactly once`() {
        val rows = NetworkDemoGeographyData.coverages()

        assertEquals(388, rows.size)
        assertEquals(388, rows.map { it.wardCode }.distinct().size)
        assertEquals(mapOf("01" to 126, "48" to 94, "79" to 168), rows.groupingBy { it.provinceCode }.eachCount())
        assertEquals(37, NetworkDemoGeographyData.serviceAreas.size)
        assertEquals(3, NetworkDemoGeographyData.gateways.size)
        assertTrue(rows.any { it.tier == CoverageTier.REMOTE })
        assertEquals(5, rows.count { it.tier == CoverageTier.ISLAND })
    }

    @Test
    fun `every service area owns at least one mapped unit and has a valid representative hub location`() {
        val rows = NetworkDemoGeographyData.coverages()
        val byArea = rows.groupBy { it.serviceAreaCode }

        NetworkDemoGeographyData.serviceAreas.forEach { area ->
            val areaRows = requireNotNull(byArea[area.code])
            assertTrue(areaRows.isNotEmpty(), "${area.code} must own at least one coverage")
            assertTrue(
                areaRows.any { it.wardCode == area.hubWardCode },
                "${area.hubCode} representative ward must belong to ${area.code}",
            )
        }
    }
}
