package com.pavex.network.seed

import com.pavex.network.entity.TransportMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkDemoTransportDataTest {
    @Test
    fun `demo transport has the expected operating scale`() {
        val lanes = NetworkDemoTransportData.lanes()
        val templates = NetworkDemoTransportData.routeTemplates()

        assertEquals(86, lanes.size)
        assertEquals(252, lanes.sumOf { it.schedules.size })
        assertEquals(18, templates.size)
        assertEquals(74, lanes.count { it.transportMode == TransportMode.ROAD && (it.fromHubCode.endsWith("-GW").xor(it.toHubCode.endsWith("-GW"))) })
        assertEquals(6, lanes.count { it.transportMode == TransportMode.AIR })
        assertEquals(80, lanes.count { it.transportMode == TransportMode.ROAD })
    }

    @Test
    fun `every geography area has a two-way feeder connection`() {
        val codes = NetworkDemoTransportData.lanes().map { it.code }.toSet()

        NetworkDemoGeographyData.serviceAreas.forEach { area ->
            assertTrue(NetworkDemoTransportData.feederOutCode(area.hubCode, area.gatewayCode) in codes)
            assertTrue(NetworkDemoTransportData.feederInCode(area.gatewayCode, area.hubCode) in codes)
        }
    }

    @Test
    fun `major metro pairs have express standard and economy templates`() {
        val codes = NetworkDemoTransportData.routeTemplates().map { it.code }.toSet()
        val pairs = listOf("HN-DN", "DN-HN", "HN-HCM", "HCM-HN", "DN-HCM", "HCM-DN")

        pairs.forEach { pair ->
            assertTrue("$pair-EXPRESS" in codes)
            assertTrue("$pair-STANDARD" in codes)
            assertTrue("$pair-ECONOMY" in codes)
        }
    }
}
