package com.pavex.network.service

import com.pavex.network.dto.routing.RoutePlanLegResponse
import com.pavex.network.dto.routing.RoutePlanResponse
import com.pavex.network.dto.routing.RoutePlanSource
import com.pavex.network.dto.routing.RoutePlanningMode
import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.HubType
import com.pavex.network.entity.LaneSchedule
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.entity.RouteTemplate
import com.pavex.network.entity.RouteTemplateStatus
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.HubRepository
import com.pavex.network.repository.LaneScheduleRepository
import com.pavex.network.repository.RouteTemplateLegRepository
import com.pavex.network.repository.RouteTemplateRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class NetworkRoutePlanner(
    private val resolver: NetworkResolverService,
    private val hubs: HubRepository,
    private val lanes: HubLaneRepository,
    private val schedules: LaneScheduleRepository,
    private val capacitySnapshots: DepartureCapacitySnapshotFactory,
    private val templates: RouteTemplateRepository,
    private val templateLegs: RouteTemplateLegRepository,
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
        mode: RoutePlanningMode = RoutePlanningMode.PREFER_TEMPLATE,
        excludedDepartures: Set<DepartureKey> = emptySet(),
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
                source = RoutePlanSource.LOCAL,
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

        // Local hubs are leaves of the hub hierarchy, so a sensible route stays on the two
        // branches and the provincial/regional backbone. Loading only that corridor keeps a
        // quote cheap when every ward has its own post office; the whole network is the fallback.
        val corridor = loadGraph(corridorHubIds(origin.primaryHubId, destination.primaryHubId), shipmentWeightKg, readyAt, excludedDepartures)
        var wholeNetwork: PlanningGraph? = null
        fun whole() = wholeNetwork ?: loadGraph(null, shipmentWeightKg, readyAt, excludedDepartures).also { wholeNetwork = it }

        if (mode != RoutePlanningMode.GRAPH_ONLY) {
            val candidates = templates.findAllByOriginHubIdAndDestinationHubIdAndStatusOrderByPriorityAscCodeAsc(
                origin.primaryHubId,
                destination.primaryHubId,
                RouteTemplateStatus.ACTIVE,
            )
            // A preferred journey may name any lane, so it is checked against the whole network.
            val graph = if (candidates.isEmpty()) null else whole()
            val preferred = graph?.let {
                tryPreferredTemplate(candidates, readyAt, shipmentWeightKg, it.lanes, it.schedules, it.capacity)
            }
            if (preferred != null) {
                return buildPlan(
                    source = RoutePlanSource.ROUTE_TEMPLATE,
                    template = preferred.template,
                    originServiceAreaId = origin.serviceAreaId,
                    destinationServiceAreaId = destination.serviceAreaId,
                    originHubId = origin.primaryHubId,
                    originHubCode = origin.primaryHubCode,
                    destinationHubId = destination.primaryHubId,
                    destinationHubCode = destination.primaryHubCode,
                    shipmentWeightKg = shipmentWeightKg,
                    readyAt = readyAt,
                    path = preferred.path,
                    activeHubs = graph.hubs,
                    capacity = graph.capacity,
                )
            }
            if (mode == RoutePlanningMode.TEMPLATE_ONLY) throw templateRouteNotFound()
        }

        fun findPath(graph: PlanningGraph) = if (graph.lanes.isEmpty()) null else EarliestArrivalPathFinder.find(
            originHubId = origin.primaryHubId,
            destinationHubId = destination.primaryHubId,
            readyAt = readyAt,
            lanes = graph.lanes,
            schedules = graph.schedules,
            transferBufferMinutes = transferBufferMinutes,
            scheduleSearchDays = scheduleSearchDays,
            shipmentWeightKg = shipmentWeightKg,
            capacity = graph.capacity,
        )

        val corridorPath = findPath(corridor)
        val graph = if (corridorPath != null) corridor else whole()
        val path = corridorPath ?: findPath(graph) ?: throw routeNotFound()

        return buildPlan(
            source = RoutePlanSource.GRAPH,
            template = null,
            originServiceAreaId = origin.serviceAreaId,
            destinationServiceAreaId = destination.serviceAreaId,
            originHubId = origin.primaryHubId,
            originHubCode = origin.primaryHubCode,
            destinationHubId = destination.primaryHubId,
            destinationHubCode = destination.primaryHubCode,
            shipmentWeightKg = shipmentWeightKg,
            readyAt = readyAt,
            path = path,
            activeHubs = graph.hubs,
            capacity = graph.capacity,
        )
    }

    private data class PlanningGraph(
        val hubs: Map<UUID, Hub>,
        val lanes: List<HubLane>,
        val schedules: List<LaneSchedule>,
        val capacity: DepartureCapacitySnapshot,
    )

    /** Active lanes, schedules and booked capacity among [hubIds], or across the whole network when null. */
    private fun loadGraph(
        hubIds: Set<UUID>?,
        shipmentWeightKg: BigDecimal,
        readyAt: Instant,
        excludedDepartures: Set<DepartureKey>,
    ): PlanningGraph {
        val activeHubs = (if (hubIds == null) hubs.findAllByStatus(HubStatus.ACTIVE) else hubs.findAllById(hubIds))
            .filter { it.status == HubStatus.ACTIVE }
            .associateBy { it.id }
        val eligibleLanes = (
            if (hubIds == null) {
                lanes.findAllByStatusAndMaxShipmentWeightKgGreaterThanEqual(HubLaneStatus.ACTIVE, shipmentWeightKg)
            } else {
                lanes.findAllByStatusAndMaxShipmentWeightKgGreaterThanEqualAndFromHubIdInAndToHubIdIn(
                    HubLaneStatus.ACTIVE,
                    shipmentWeightKg,
                    hubIds,
                    hubIds,
                )
            }
            ).filter { it.fromHubId in activeHubs && it.toHubId in activeHubs }
        val activeSchedules = if (eligibleLanes.isEmpty()) {
            emptyList()
        } else {
            schedules.findAllByLaneIdInAndStatus(eligibleLanes.map { it.id }, LaneScheduleStatus.ACTIVE)
        }
        val capacity = capacitySnapshots.build(activeSchedules, readyAt, scheduleSearchDays, excludedDepartures)
        return PlanningGraph(activeHubs, eligibleLanes, activeSchedules, capacity)
    }

    private fun corridorHubIds(originHubId: UUID, destinationHubId: UUID): Set<UUID> {
        val backbone = hubs.findAllByStatusAndTypeIn(HubStatus.ACTIVE, BACKBONE_TYPES).map { it.id }
        return (backbone + ancestry(originHubId) + ancestry(destinationHubId)).toSet()
    }

    /** The hub and its parents up to the regional gateway. */
    private fun ancestry(hubId: UUID): List<UUID> {
        val chain = mutableListOf(hubId)
        var parent = hubs.findById(hubId).orElse(null)?.parentHubId
        while (parent != null && parent !in chain && chain.size < MAX_HIERARCHY_DEPTH) {
            chain += parent
            parent = hubs.findById(parent).orElse(null)?.parentHubId
        }
        return chain
    }

    private fun tryPreferredTemplate(
        candidates: List<RouteTemplate>,
        readyAt: Instant,
        shipmentWeightKg: BigDecimal,
        eligibleLanes: List<HubLane>,
        activeSchedules: List<LaneSchedule>,
        capacity: DepartureCapacitySnapshot,
    ): PreferredTemplatePlan? {

        val legsByTemplate = templateLegs
            .findAllByRouteTemplateIdInOrderByRouteTemplateIdAscSequenceAsc(candidates.map { it.id })
            .groupBy { it.routeTemplateId }
        val laneLookup = eligibleLanes.associateBy { it.id }

        candidates.forEach { template ->
            val legRows = legsByTemplate[template.id].orEmpty().sortedBy { it.sequence }
            if (legRows.isEmpty()) return@forEach
            val path = legRows.mapNotNull { laneLookup[it.laneId] }
            if (path.size != legRows.size || !matchesEndpoints(template, path)) return@forEach

            val planned = RouteTemplateJourneyPlanner.plan(
                readyAt = readyAt,
                lanes = path,
                schedules = activeSchedules,
                transferBufferMinutes = transferBufferMinutes,
                scheduleSearchDays = scheduleSearchDays,
                shipmentWeightKg = shipmentWeightKg,
                capacity = capacity,
            ) ?: return@forEach
            return PreferredTemplatePlan(template, planned)
        }
        return null
    }

    private fun matchesEndpoints(template: RouteTemplate, path: List<HubLane>): Boolean {
        if (path.isEmpty()) return false
        if (path.first().fromHubId != template.originHubId || path.last().toHubId != template.destinationHubId) return false
        return path.zipWithNext().all { (left, right) -> left.toHubId == right.fromHubId }
    }

    private fun buildPlan(
        source: RoutePlanSource,
        template: RouteTemplate?,
        originServiceAreaId: UUID,
        destinationServiceAreaId: UUID,
        originHubId: UUID,
        originHubCode: String,
        destinationHubId: UUID,
        destinationHubCode: String,
        shipmentWeightKg: BigDecimal,
        readyAt: Instant,
        path: List<PlannedGraphLeg>,
        activeHubs: Map<UUID, Hub>,
        capacity: DepartureCapacitySnapshot,
    ): RoutePlanResponse {
        val arrivalAt = path.last().arrivalAt
        val allDistancesKnown = path.all { it.lane.distanceKm != null }
        val totalDistance = if (allDistancesKnown) {
            path.fold(BigDecimal.ZERO) { total, leg -> total.add(requireNotNull(leg.lane.distanceKm)) }
        } else {
            null
        }

        return RoutePlanResponse(
            source = source,
            routeTemplateId = template?.id,
            routeTemplateCode = template?.code,
            routeTemplateRevision = template?.revision,
            originServiceAreaId = originServiceAreaId,
            destinationServiceAreaId = destinationServiceAreaId,
            originHubId = originHubId,
            originHubCode = originHubCode,
            destinationHubId = destinationHubId,
            destinationHubCode = destinationHubCode,
            shipmentWeightKg = shipmentWeightKg,
            readyAt = readyAt,
            arrivalAt = arrivalAt,
            totalJourneyMinutes = Duration.between(readyAt, arrivalAt).toMinutes(),
            inTransitMinutes = path.sumOf { it.lane.estimatedTransitMinutes },
            totalDistanceKm = totalDistance,
            legs = path.mapIndexed { index, leg ->
                val allocated = capacity.allocated(leg.schedule, leg.serviceDate)
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
                    departureCapacityKg = leg.schedule.capacityKg,
                    allocatedCapacityKg = allocated,
                    remainingCapacityKg = leg.schedule.capacityKg.subtract(allocated).max(BigDecimal.ZERO),
                )
            },
        )
    }

    private fun routeNotFound() = NotFoundException(
        "route_not_found",
        "No active scheduled transport route with enough remaining capacity can carry this shipment between the resolved hubs.",
    )

    private fun templateRouteNotFound() = NotFoundException(
        "route_template_not_available",
        "No active route template with enough remaining capacity can currently carry this shipment between the resolved hubs.",
    )

    private data class PreferredTemplatePlan(
        val template: RouteTemplate,
        val path: List<PlannedGraphLeg>,
    )


    companion object {
        private val BACKBONE_TYPES = listOf(HubType.PROVINCIAL, HubType.REGIONAL_GATEWAY)
        private const val MAX_HIERARCHY_DEPTH = 8
    }
}
