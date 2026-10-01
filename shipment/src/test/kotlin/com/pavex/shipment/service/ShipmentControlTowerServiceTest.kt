package com.pavex.shipment.service

import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.config.ShipmentControlTowerProperties
import com.pavex.shipment.dto.ShipmentControlAlertType
import com.pavex.shipment.repository.DeliveryAttemptRepository
import com.pavex.shipment.repository.ShipmentControlSignalRepository
import com.pavex.shipment.repository.ShipmentControlTowerReadRepository
import com.pavex.shipment.repository.ShipmentRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Opening the control tower scans shipments once; paging and filtering reuse that scan for a while. */
class ShipmentControlTowerServiceTest {
    private var now = Instant.parse("2026-10-01T08:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
    private val reads = mock(ShipmentControlTowerReadRepository::class.java)
    private val service = ShipmentControlTowerService(
        reads,
        mock(DeliveryAttemptRepository::class.java),
        mock(ShipmentRepository::class.java),
        mock(ShipmentControlSignalRepository::class.java),
        ShipmentControlTowerAccessService(mock(OperationsShipmentClient::class.java)),
        ShipmentControlTowerProperties(),
        ShipmentControlTowerMetrics(SimpleMeterRegistry()),
        clock,
    )
    private val manager = UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(),
        "n/a",
        listOf("scope.all-hubs", "shipments.control-tower.read").map(::SimpleGrantedAuthority),
    )

    @Test
    fun `summary and alerts come from one scan until it expires`() {
        val overview = service.overview(manager, null, null, null, null, 1, 50)
        service.search(manager, null, null, null, "PAVEX", 2, 50)
        service.summary(manager, null)

        assertEquals(1, scans())
        assertEquals(overview.summary.total.toLong(), overview.alerts.totalCount)
        assertEquals(ShipmentControlAlertType.entries.toSet(), overview.summary.byType.keys)

        now = now.plus(Duration.ofSeconds(16))
        service.summary(manager, null)

        assertEquals(2, scans())
    }

    private fun scans() = mockingDetails(reads).invocations.count { it.method.name == "findCandidates" }
}
