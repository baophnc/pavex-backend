package com.pavex.network.service

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.routing.CreateRouteTemplateRequest
import com.pavex.network.dto.routing.RouteTemplateJourneyResponse
import com.pavex.network.dto.routing.RoutePlanLegResponse
import com.pavex.network.dto.routing.RouteTemplateResponse
import com.pavex.network.dto.routing.UpdateRouteTemplateRequest
import com.pavex.network.dto.routing.UpdateRouteTemplateStatusRequest
import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.RouteTemplate
import com.pavex.network.entity.RouteTemplateLeg
import com.pavex.network.entity.RouteTemplateStatus
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.HubRepository
import com.pavex.network.repository.LaneScheduleRepository
import com.pavex.network.repository.RouteTemplateLegRepository
import com.pavex.network.repository.RouteTemplateRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RouteTemplateService(
    private val templates: RouteTemplateRepository,
    private val templateLegs: RouteTemplateLegRepository,
    private val lanes: HubLaneRepository,
    private val schedules: LaneScheduleRepository,
    private val capacitySnapshots: DepartureCapacitySnapshotFactory,
    private val hubs: HubRepository,
    private val audit: NetworkAuditService,
    @Value("\${network.routing.transfer-buffer-minutes:30}")
    private val transferBufferMinutes: Int,
    @Value("\${network.routing.schedule-search-days:30}")
    private val scheduleSearchDays: Int,
) {
    @Transactional(readOnly = true)
    fun list(
        status: RouteTemplateStatus?,
        originHubId: UUID?,
        destinationHubId: UUID?,
        search: String?,
        page: Int,
        pageSize: Int,
    ): PageResponse<RouteTemplateResponse> {
        val result = templates.searchAdmin(
            status,
            originHubId,
            destinationHubId,
            search?.trim().orEmpty(),
            PageRequest.of(
                page.coerceAtLeast(1) - 1,
                pageSize.coerceIn(1, 100),
                Sort.by(Sort.Order.asc("priority"), Sort.Order.asc("code")),
            ),
        )
        val lookup = responseLookup(result.content)
        return PageResponse(
            items = result.content.map { template -> buildResponse(template, lookup) },
            page = result.number + 1,
            pageSize = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): RouteTemplateResponse {
        val template = find(id)
        return buildResponse(template, responseLookup(listOf(template)))
    }

    @Transactional
    fun create(request: CreateRouteTemplateRequest, actorUserId: UUID): RouteTemplateResponse {
        val code = request.code.trim().uppercase()
        if (templates.existsByCodeIgnoreCase(code)) {
            throw ConflictException("route_template_code_exists", "Route template code already exists.")
        }
        validateMetadata(request.originHubId, request.destinationHubId, request.priority)
        val path = loadAndValidatePath(request.originHubId, request.destinationHubId, request.laneIds)
        val template = templates.save(
            RouteTemplate(
                code = code,
                name = request.name.trim(),
                description = request.description?.trim()?.takeIf { it.isNotBlank() },
                originHubId = request.originHubId,
                destinationHubId = request.destinationHubId,
                priority = request.priority,
                status = RouteTemplateStatus.INACTIVE,
            ),
        )
        templateLegs.saveAll(path.mapIndexed { index, lane ->
            RouteTemplateLeg(routeTemplateId = template.id, sequence = index + 1, laneId = lane.id)
        })
        val response = buildResponse(template, path, loadHubs(path))
        audit.record(NetworkAuditEntityType.ROUTE_TEMPLATE, template.id, NetworkAuditAction.CREATED, actorUserId, after = response)
        return response
    }

    @Transactional
    fun update(id: UUID, request: UpdateRouteTemplateRequest, actorUserId: UUID): RouteTemplateResponse {
        val template = find(id)
        val before = buildResponse(template, responseLookup(listOf(template)))
        validateMetadata(request.originHubId, request.destinationHubId, request.priority)
        val path = loadAndValidatePath(request.originHubId, request.destinationHubId, request.laneIds)
        if (template.status == RouteTemplateStatus.ACTIVE) ensureOperational(path)

        template.update(
            name = request.name.trim(),
            description = request.description?.trim()?.takeIf { it.isNotBlank() },
            originHubId = request.originHubId,
            destinationHubId = request.destinationHubId,
            priority = request.priority,
        )
        templates.save(template)
        templateLegs.deleteAllByRouteTemplateId(template.id)
        templateLegs.flush()
        templateLegs.saveAll(path.mapIndexed { index, lane ->
            RouteTemplateLeg(routeTemplateId = template.id, sequence = index + 1, laneId = lane.id)
        })
        val response = buildResponse(template, path, loadHubs(path))
        audit.record(NetworkAuditEntityType.ROUTE_TEMPLATE, template.id, NetworkAuditAction.UPDATED, actorUserId, before, response)
        return response
    }

    @Transactional(readOnly = true)
    fun lookupJourney(
        originHubId: UUID,
        destinationHubId: UUID,
        shipmentWeightKg: BigDecimal,
        readyAt: Instant,
    ): RouteTemplateJourneyResponse {
        validateEndpoints(originHubId, destinationHubId)
        if (shipmentWeightKg.signum() <= 0) {
            throw InvalidNetworkRequestException("invalid_shipment_weight", "Shipment weight must be positive.")
        }
        if (transferBufferMinutes !in 0..1440 || scheduleSearchDays !in 1..366) {
            throw IllegalStateException("Network routing configuration is invalid.")
        }

        val candidates = templates.findAllByOriginHubIdAndDestinationHubIdAndStatusOrderByPriorityAscCodeAsc(
            originHubId,
            destinationHubId,
            RouteTemplateStatus.ACTIVE,
        )
        if (candidates.isEmpty()) throw templateJourneyNotFound()

        val lookup = responseLookup(candidates)
        val allLaneIds = lookup.paths.values.flatten().map { it.id }.distinct()
        val activeSchedules = if (allLaneIds.isEmpty()) emptyList() else
            schedules.findAllByLaneIdInAndStatus(allLaneIds, LaneScheduleStatus.ACTIVE)
        val capacity = capacitySnapshots.build(activeSchedules, readyAt, scheduleSearchDays)

        candidates.forEach { template ->
            val path = lookup.paths[template.id].orEmpty()
            if (path.isEmpty() ||
                path.any { it.status != HubLaneStatus.ACTIVE || it.maxShipmentWeightKg < shipmentWeightKg } ||
                path.any { lookup.hubs[it.fromHubId]?.status != HubStatus.ACTIVE || lookup.hubs[it.toHubId]?.status != HubStatus.ACTIVE }
            ) return@forEach

            val planned = RouteTemplateJourneyPlanner.plan(
                readyAt = readyAt,
                lanes = path,
                schedules = activeSchedules,
                transferBufferMinutes = transferBufferMinutes,
                scheduleSearchDays = scheduleSearchDays,
                shipmentWeightKg = shipmentWeightKg,
                capacity = capacity,
            ) ?: return@forEach

            val arrivalAt = planned.last().arrivalAt
            val totalDistance = if (planned.all { it.lane.distanceKm != null }) {
                planned.fold(BigDecimal.ZERO) { total, leg -> total.add(requireNotNull(leg.lane.distanceKm)) }
            } else null
            return RouteTemplateJourneyResponse(
                routeTemplateId = template.id,
                routeTemplateCode = template.code,
                routeTemplateName = template.name,
                priority = template.priority,
                revision = template.revision,
                originHubId = originHubId,
                originHubCode = lookup.hubs.getValue(originHubId).code,
                destinationHubId = destinationHubId,
                destinationHubCode = lookup.hubs.getValue(destinationHubId).code,
                shipmentWeightKg = shipmentWeightKg,
                readyAt = readyAt,
                arrivalAt = arrivalAt,
                totalJourneyMinutes = Duration.between(readyAt, arrivalAt).toMinutes(),
                inTransitMinutes = planned.sumOf { it.lane.estimatedTransitMinutes },
                totalDistanceKm = totalDistance,
                legs = planned.mapIndexed { index, leg ->
                    RoutePlanLegResponse(
                        sequence = index + 1,
                        laneId = leg.lane.id,
                        laneCode = leg.lane.code,
                        scheduleId = leg.schedule.id,
                        fromHubId = leg.lane.fromHubId,
                        fromHubCode = lookup.hubs.getValue(leg.lane.fromHubId).code,
                        toHubId = leg.lane.toHubId,
                        toHubCode = lookup.hubs.getValue(leg.lane.toHubId).code,
                        transportMode = leg.lane.transportMode,
                        serviceDate = leg.serviceDate,
                        scheduledDepartureTime = leg.schedule.departureTime,
                        departureAt = leg.departureAt,
                        arrivalAt = leg.arrivalAt,
                        waitMinutes = leg.waitMinutes,
                        transitMinutes = leg.lane.estimatedTransitMinutes,
                        distanceKm = leg.lane.distanceKm,
                        departureCapacityKg = leg.schedule.capacityKg,
                        allocatedCapacityKg = capacity.allocated(leg.schedule, leg.serviceDate),
                        remainingCapacityKg = capacity.remaining(leg.schedule, leg.serviceDate),
                    )
                },
            )
        }
        throw templateJourneyNotFound()
    }

    @Transactional
    fun updateStatus(
        id: UUID,
        request: UpdateRouteTemplateStatusRequest,
        actorUserId: UUID,
    ): RouteTemplateResponse {
        val template = find(id)
        val lookup = responseLookup(listOf(template))
        val before = buildResponse(template, lookup)
        val path = lookup.paths.getValue(template.id)
        if (request.active) ensureOperational(path)

        template.setActive(request.active)
        templates.save(template)
        val response = buildResponse(template, path, lookup.hubs)
        audit.record(
            NetworkAuditEntityType.ROUTE_TEMPLATE,
            template.id,
            if (request.active) NetworkAuditAction.ACTIVATED else NetworkAuditAction.DEACTIVATED,
            actorUserId,
            before,
            response,
            request.reason,
        )
        return response
    }

    @Transactional
    fun delete(id: UUID, reason: String?, actorUserId: UUID) {
        if (reason != null && reason.length > 500) {
            throw InvalidNetworkRequestException("invalid_delete_reason", "Delete reason cannot exceed 500 characters.")
        }
        val template = find(id)
        if (template.status == RouteTemplateStatus.ACTIVE) {
            throw ConflictException(
                "active_route_template_delete",
                "Deactivate the route template before deleting it.",
            )
        }
        val before = buildResponse(template, responseLookup(listOf(template)))
        templates.delete(template)
        audit.record(
            NetworkAuditEntityType.ROUTE_TEMPLATE,
            template.id,
            NetworkAuditAction.DELETED,
            actorUserId,
            before = before,
            reason = reason,
        )
    }

    fun find(id: UUID): RouteTemplate = templates.findById(id).orElseThrow {
        NotFoundException("route_template_not_found", "Route template was not found.")
    }

    private fun validateMetadata(originHubId: UUID, destinationHubId: UUID, priority: Int) {
        validateEndpoints(originHubId, destinationHubId)
        if (priority !in 1..10000) {
            throw InvalidNetworkRequestException("invalid_route_template_priority", "Route template priority must be between 1 and 10000.")
        }
    }

    private fun validateEndpoints(originHubId: UUID, destinationHubId: UUID) {
        if (originHubId == destinationHubId) {
            throw InvalidNetworkRequestException(
                "route_template_same_hub",
                "Route template origin and destination hubs must be different.",
            )
        }
    }

    private fun loadAndValidatePath(
        originHubId: UUID,
        destinationHubId: UUID,
        laneIds: List<UUID>,
    ): List<HubLane> {
        if (laneIds.isEmpty()) {
            throw InvalidNetworkRequestException("route_template_lanes_required", "Route template requires at least one lane.")
        }
        if (laneIds.distinct().size != laneIds.size) {
            throw InvalidNetworkRequestException("route_template_lane_repeated", "Route template cannot repeat a lane.")
        }
        val laneLookup = lanes.findAllById(laneIds).associateBy { it.id }
        if (laneLookup.size != laneIds.size) {
            throw InvalidNetworkRequestException("route_template_lane_missing", "One or more route template lanes do not exist.")
        }
        val path = laneIds.map { laneLookup.getValue(it) }
        validatePath(originHubId, destinationHubId, path)
        return path
    }

    private fun validatePath(originHubId: UUID, destinationHubId: UUID, path: List<HubLane>) {
        if (path.first().fromHubId != originHubId) {
            throw InvalidNetworkRequestException(
                "route_template_origin_mismatch",
                "The first route template lane must start at the origin hub.",
            )
        }
        if (path.last().toHubId != destinationHubId) {
            throw InvalidNetworkRequestException(
                "route_template_destination_mismatch",
                "The last route template lane must end at the destination hub.",
            )
        }
        path.zipWithNext().forEachIndexed { index, (left, right) ->
            if (left.toHubId != right.fromHubId) {
                throw InvalidNetworkRequestException(
                    "route_template_disconnected",
                    "Route template lane ${index + 2} does not continue from the previous lane.",
                )
            }
        }
        val visited = mutableSetOf(originHubId)
        path.forEach { lane ->
            if (!visited.add(lane.toHubId)) {
                throw InvalidNetworkRequestException("route_template_cycle", "Route template cannot contain a hub cycle.")
            }
        }
    }

    private fun ensureOperational(path: List<HubLane>) {
        if (path.any { it.status != HubLaneStatus.ACTIVE }) {
            throw ConflictException(
                "route_template_lane_inactive",
                "All route template lanes must be active before activating the template.",
            )
        }
        val hubLookup = loadHubs(path)
        if (hubLookup.values.any { it.status != HubStatus.ACTIVE }) {
            throw ConflictException(
                "route_template_hub_inactive",
                "All hubs used by the route template must be active before activating it.",
            )
        }
        val laneIds = path.map { it.id }
        val activeSchedules = schedules.findAllByLaneIdInAndStatus(laneIds, LaneScheduleStatus.ACTIVE).groupBy { it.laneId }
        if (laneIds.any { laneId -> activeSchedules[laneId].orEmpty().none(LaneScheduleService::canOperateFromToday) }) {
            throw ConflictException(
                "route_template_schedule_required",
                "Every route template lane needs at least one active current or future schedule.",
            )
        }
    }

    private fun responseLookup(items: Collection<RouteTemplate>): ResponseLookup {
        if (items.isEmpty()) return ResponseLookup(emptyMap(), emptyMap())
        val templateIds = items.map { it.id }
        val legRows = templateLegs.findAllByRouteTemplateIdInOrderByRouteTemplateIdAscSequenceAsc(templateIds)
        val laneLookup = lanes.findAllById(legRows.map { it.laneId }).associateBy { it.id }
        val paths = legRows.groupBy { it.routeTemplateId }.mapValues { (_, legs) ->
            legs.sortedBy { it.sequence }.map { leg ->
                laneLookup[leg.laneId] ?: throw IllegalStateException("Route template references a missing lane.")
            }
        }
        val allPaths = paths.values.flatten()
        return ResponseLookup(paths, loadHubs(allPaths))
    }

    private fun buildResponse(template: RouteTemplate, lookup: ResponseLookup): RouteTemplateResponse =
        buildResponse(template, lookup.paths[template.id].orEmpty(), lookup.hubs)

    private fun buildResponse(template: RouteTemplate, path: List<HubLane>, hubLookup: Map<UUID, Hub>): RouteTemplateResponse {
        if (path.isEmpty()) throw IllegalStateException("Route template has no legs.")
        return RouteTemplateResponse.from(template, path, hubLookup)
    }

    private fun loadHubs(path: Collection<HubLane>): Map<UUID, Hub> {
        val ids = path.flatMap { listOf(it.fromHubId, it.toHubId) }.toSet()
        return hubs.findAllById(ids).associateBy { it.id }
    }

    private fun templateJourneyNotFound() = NotFoundException(
        "route_template_not_available",
        "No active route template with enough remaining departure capacity can currently carry this shipment between the selected hubs.",
    )

    private data class ResponseLookup(
        val paths: Map<UUID, List<HubLane>>,
        val hubs: Map<UUID, Hub>,
    )
}
