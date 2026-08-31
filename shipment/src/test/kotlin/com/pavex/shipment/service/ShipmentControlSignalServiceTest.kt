package com.pavex.shipment.service

import com.pavex.shipment.dto.RecordParcelScanRequest
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.repository.ShipmentControlSignalRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ShipmentControlSignalServiceTest {
    private val repository = mock(ShipmentControlSignalRepository::class.java)
    private val now = Instant.parse("2026-08-31T10:00:00Z")
    private val service = ShipmentControlSignalService(repository, Clock.fixed(now, ZoneOffset.UTC))
    private val target = ParcelScanTarget(
        shipmentId = UUID.randomUUID(),
        parcelId = UUID.randomUUID(),
        parcelCode = "PAVEX-P01",
        routeRevision = 1,
        originHubId = UUID.randomUUID(),
        destinationHubId = UUID.randomUUID(),
        lifecycleStatus = ShipmentLifecycleStatus.ACTIVE,
        parcelStatus = ParcelStatus.IN_TRANSIT,
        isOnHold = false,
    )

    @Test
    fun `route mismatch creates control signal`() {
        service.recordRejectedMovement(
            UUID.randomUUID(),
            target,
            RecordParcelScanRequest(target.parcelCode, UUID.randomUUID(), ParcelStatus.AT_DESTINATION_HUB),
            InvalidShipmentRequestException("scan_hub_mismatch", "Wrong hub"),
        )
        verify(repository).save(any())
    }

    @Test
    fun `ordinary validation failure does not create route signal`() {
        service.recordRejectedMovement(
            UUID.randomUUID(),
            target,
            RecordParcelScanRequest(target.parcelCode, UUID.randomUUID(), ParcelStatus.AT_DESTINATION_HUB),
            InvalidShipmentRequestException("hub_not_active", "Inactive hub"),
        )
        verify(repository, never()).save(any())
    }
}
