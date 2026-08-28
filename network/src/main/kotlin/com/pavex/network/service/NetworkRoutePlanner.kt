package com.pavex.network.service

import com.pavex.network.dto.routing.RoutePlanLegResponse
import com.pavex.network.dto.routing.RoutePlanResponse
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.HubRepository
import com.pavex.network.repository.LaneScheduleRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@Service
class NetworkRoutePlanner(
    private val resolver: NetworkResolverService,
    private val hubs: HubRepository,
    private val lanes: HubLaneRepository,
    private val schedules: LaneScheduleRepository,
    @Value("\${network.routing.transfer-buffer-minutes:30}")
    private val transferBufferMinutes: Int,
    @Value("\${network.routing.schedule-search-days:30}")
    private val scheduleSearchDays: Int,
) {
    @Transactional(readOnly = true)
    fun plan(
        originProvinceCode: String,
        originWardCode: String,
        destinationProvinceCode: String,
        destinationWardCode: String,
        shipmentWeightKg: BigDecimal,
        readyAt: Instant,
    ): RoutePlanResponse {
        if (shipmentWeightKg.signum() <= 0) {
            throw InvalidNetworkRequestException("invalid_shipment_weight", "Shipment weight must be positive.")
        }
        if (transferBufferMinutes !in 0..1440 || scheduleSearchDays !in 1..366) {
            throw IllegalStateException("Network routing configuration is invalid.")
        }

        val origin = resolver.resolve(originProvinceCode, originWardCode)
        val destination = resolver.resolve(destinationProvinceCode, destinationWardCode)
        if (origin.primaryHubId == destination.primaryHubId) {
            return RoutePlanResponse(
                originServiceAreaId = origin.serviceAreaId,
                destinationServiceAreaId = destination.serviceAreaId,
                originHubId = origin.primaryHubId,
                originHubCode = origin.primaryHubCode,
                destinationHubId = destination.primaryHubId,
                destinationHubCode = destination.primaryHubCode,
                shipmentWeightKg = shipmentWeightKg,
                readyAt = readyAt,
                arrivalAt = readyAt,
                totalJourneyMinutes = 0,
                inTransitMinutes = 0,
                totalDistanceKm = BigDecimal.ZERO,
                legs = emptyList(),
            )
        }

        val activeHubs = hubs.findAllByStatus(HubStatus.ACTIVE).associateBy { it.id }
        val eligibleLanes = lanes.findAllByStatusAndMaxShipmentWeightKgGreaterThanEqual(
            HubLaneStatus.ACTIVE,
            shipmentWeightKg,
        ).filter { it.fromHubId in activeHubs && it.toHubId in activeHubs }

        if (eligibleLanes.isEmpty()) throw routeNotFound()
        val laneIds = eligibleLanes.map { it.id }
        val activeSchedules = schedules.findAllByLaneIdInAndStatus(laneIds, LaneScheduleStatus.ACTIVE)
        val path = EarliestArrivalPathFinder.find(
            originHubId = origin.primaryHubId,
            destinationHubId = destination.primaryHubId,
            readyAt = readyAt,
            lanes = eligibleLanes,
            schedules = activeSchedules,
            transferBufferMinutes = transferBufferMinutes,
            scheduleSearchDays = scheduleSearchDays,
        ) ?: throw routeNotFound()

        val arrivalAt = path.last().arrivalAt
        val allDistancesKnown = path.all { it.lane.distanceKm != null }
        val totalDistance = if (allDistancesKnown) {
            path.fold(BigDecimal.ZERO) { total, leg -> total.add(requireNotNull(leg.lane.distanceKm)) }
        } else {
            null
        }

        return RoutePlanResponse(
            originServiceAreaId = origin.serviceAreaId,
            destinationServiceAreaId = destination.serviceAreaId,
            originHubId = origin.primaryHubId,
            originHubCode = origin.primaryHubCode,
            destinationHubId = destination.primaryHubId,
            destinationHubCode = destination.primaryHubCode,
            shipmentWeightKg = shipmentWeightKg,
            readyAt = readyAt,
            arrivalAt = arrivalAt,
            totalJourneyMinutes = Duration.between(readyAt, arrivalAt).toMinutes(),
            inTransitMinutes = path.sumOf { it.lane.estimatedTransitMinutes },
            totalDistanceKm = totalDistance,
            legs = path.mapIndexed { index, leg ->
                RoutePlanLegResponse(
                    sequence = index + 1,
                    laneId = leg.lane.id,
                    laneCode = leg.lane.code,
                    scheduleId = leg.schedule.id,
                    fromHubId = leg.lane.fromHubId,
                    fromHubCode = activeHubs.getValue(leg.lane.fromHubId).code,
                    toHubId = leg.lane.toHubId,
                    toHubCode = activeHubs.getValue(leg.lane.toHubId).code,
                    transportMode = leg.lane.transportMode,
                    serviceDate = leg.serviceDate,
                    scheduledDepartureTime = leg.schedule.departureTime,
                    departureAt = leg.departureAt,
                    arrivalAt = leg.arrivalAt,
                    waitMinutes = leg.waitMinutes,
                    transitMinutes = leg.lane.estimatedTransitMinutes,
                    distanceKm = leg.lane.distanceKm,
                )
            },
        )
    }

    private fun routeNotFound() = NotFoundException(
        "route_not_found",
        "No active scheduled transport route can carry this shipment between the resolved hubs.",
    )
}
