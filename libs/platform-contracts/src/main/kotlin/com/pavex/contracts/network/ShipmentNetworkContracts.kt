package com.pavex.contracts.network

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ReserveShipmentNetworkRequest(
    val shipmentId: UUID,
    val actorUserId: UUID,
    val routeRevision: Int,
    val originProvinceCode: String,
    val originWardCode: String,
    val destinationProvinceCode: String,
    val destinationWardCode: String,
    val shipmentWeightKg: BigDecimal,
    val readyAt: Instant,
    val expectedOriginServiceAreaId: UUID,
    val expectedOriginRegionId: UUID,
    val expectedOriginHubId: UUID,
    val expectedDestinationServiceAreaId: UUID,
    val expectedDestinationRegionId: UUID,
    val expectedDestinationHubId: UUID,
)

data class ShipmentNetworkPointSnapshot(
    val serviceAreaId: UUID,
    val regionId: UUID,
    val hubId: UUID,
    val hubCode: String,
    val hubName: String,
    val coverageTier: NetworkCoverageTier,
)

data class ShipmentNetworkReservationSnapshot(
    val shipmentId: UUID,
    val routeRevision: Int,
    val batchId: UUID?,
    val reservedWeightKg: BigDecimal,
    val readyAt: Instant,
    val estimatedDeliveryAt: Instant,
    val estimatedTransitMinutes: Int,
    val distanceKm: BigDecimal?,
    val status: String,
    val origin: ShipmentNetworkPointSnapshot,
    val destination: ShipmentNetworkPointSnapshot,
)

data class ReleaseShipmentNetworkRequest(
    val actorUserId: UUID,
    val reason: String? = null,
)

data class ShipmentOperationalHubSnapshot(
    val hubId: UUID,
    val hubCode: String,
    val hubName: String,
    val active: Boolean,
    val onReservedRoute: Boolean,
)
