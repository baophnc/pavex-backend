package com.pavex.network.service

import com.pavex.contracts.network.NetworkCoverageTier
import com.pavex.contracts.network.ReleaseShipmentNetworkRequest
import com.pavex.contracts.network.ReserveShipmentNetworkRequest
import com.pavex.contracts.network.ShipmentNetworkPointSnapshot
import com.pavex.contracts.network.ShipmentNetworkReservationSnapshot
import com.pavex.network.dto.capacity.ReserveLaneCapacityRequest
import com.pavex.network.dto.routing.RoutePlanningMode
import com.pavex.network.exception.ConflictException
import com.pavex.network.repository.HubLaneRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class ShipmentNetworkReservationService(
    private val resolver: NetworkResolverService,
    private val capacity: NetworkCapacityService,
    private val lanes: HubLaneRepository,
) {
    @Transactional
    fun reserve(request: ReserveShipmentNetworkRequest): ShipmentNetworkReservationSnapshot {
        require(request.routeRevision > 0) { "routeRevision must be positive." }
        require(request.shipmentWeightKg.signum() > 0) { "shipmentWeightKg must be positive." }

        val origin = resolver.resolve(request.originProvinceCode, request.originWardCode)
        val destination = resolver.resolve(request.destinationProvinceCode, request.destinationWardCode)
        validateQuoteTopology(request, origin, destination)

        val reservation = capacity.reserve(
            ReserveLaneCapacityRequest(
                shipmentId = request.shipmentId,
                routeRevision = request.routeRevision,
                originProvinceCode = request.originProvinceCode,
                originWardCode = request.originWardCode,
                destinationProvinceCode = request.destinationProvinceCode,
                destinationWardCode = request.destinationWardCode,
                shipmentWeightKg = request.shipmentWeightKg,
                readyAt = request.readyAt,
                mode = RoutePlanningMode.PREFER_TEMPLATE,
            ),
            request.actorUserId,
        )

        val reservedLanes = lanes.findAllById(reservation.legs.map { it.laneId }).associateBy { it.id }
        val orderedLanes = reservation.legs.sortedBy { it.sequence }.map { leg ->
            reservedLanes[leg.laneId] ?: throw ConflictException(
                "network_route_changed",
                "A reserved route lane is no longer available.",
            )
        }
        val distance = when {
            orderedLanes.isEmpty() -> BigDecimal.ZERO
            orderedLanes.all { it.distanceKm != null } -> orderedLanes.fold(BigDecimal.ZERO) { total, lane ->
                total.add(requireNotNull(lane.distanceKm))
            }
            else -> null
        }

        return ShipmentNetworkReservationSnapshot(
            shipmentId = request.shipmentId,
            routeRevision = reservation.routeRevision,
            batchId = reservation.batchId,
            reservedWeightKg = reservation.shipmentWeightKg,
            readyAt = request.readyAt,
            estimatedDeliveryAt = reservation.estimatedDeliveryAt,
            estimatedTransitMinutes = orderedLanes.sumOf { it.estimatedTransitMinutes },
            distanceKm = distance,
            status = reservation.status.name,
            origin = ShipmentNetworkPointSnapshot(
                serviceAreaId = origin.serviceAreaId,
                regionId = origin.regionId,
                hubId = origin.primaryHubId,
                hubCode = origin.primaryHubCode,
                hubName = origin.primaryHubName,
                coverageTier = NetworkCoverageTier.valueOf(origin.coverageTier.name),
            ),
            destination = ShipmentNetworkPointSnapshot(
                serviceAreaId = destination.serviceAreaId,
                regionId = destination.regionId,
                hubId = destination.primaryHubId,
                hubCode = destination.primaryHubCode,
                hubName = destination.primaryHubName,
                coverageTier = NetworkCoverageTier.valueOf(destination.coverageTier.name),
            ),
        )
    }

    @Transactional
    fun release(
        shipmentId: UUID,
        routeRevision: Int,
        request: ReleaseShipmentNetworkRequest,
    ) {
        capacity.release(
            shipmentId = shipmentId,
            routeRevision = routeRevision,
            actorUserId = request.actorUserId,
            reason = request.reason,
        )
    }

    private fun validateQuoteTopology(
        request: ReserveShipmentNetworkRequest,
        origin: com.pavex.network.dto.routing.ServiceAreaResolutionResponse,
        destination: com.pavex.network.dto.routing.ServiceAreaResolutionResponse,
    ) {
        val originMatches = origin.serviceAreaId == request.expectedOriginServiceAreaId &&
            origin.regionId == request.expectedOriginRegionId &&
            origin.primaryHubId == request.expectedOriginHubId
        val destinationMatches = destination.serviceAreaId == request.expectedDestinationServiceAreaId &&
            destination.regionId == request.expectedDestinationRegionId &&
            destination.primaryHubId == request.expectedDestinationHubId
        if (!originMatches || !destinationMatches) {
            throw ConflictException(
                "network_quote_topology_changed",
                "Network coverage changed after the pricing quote was created. Request a new quote before creating the shipment.",
            )
        }
    }
}
