package com.pavex.network.service

import com.pavex.network.dto.PageResponse
import com.pavex.network.dto.lane.CreateHubLaneRequest
import com.pavex.network.dto.lane.HubLaneResponse
import com.pavex.network.dto.lane.UpdateHubLaneRequest
import com.pavex.network.dto.lane.UpdateHubLaneStatusRequest
import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.entity.TransportMode
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.HubRepository
import com.pavex.network.repository.LaneScheduleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class HubLaneService(
    private val lanes: HubLaneRepository,
    private val schedules: LaneScheduleRepository,
    private val hubs: HubRepository,
    private val audit: NetworkAuditService,
) {
    @Transactional(readOnly = true)
    fun list(
        status: HubLaneStatus?,
        transportMode: TransportMode?,
        fromHubId: UUID?,
        toHubId: UUID?,
        search: String?,
        page: Int,
        pageSize: Int,
    ): PageResponse<HubLaneResponse> {
        val result = lanes.searchAdmin(
            status,
            transportMode,
            fromHubId,
            toHubId,
            search?.trim().orEmpty(),
            PageRequest.of(
                page.coerceAtLeast(1) - 1,
                pageSize.coerceIn(1, 100),
                Sort.by(Sort.Direction.ASC, "code"),
            ),
        )
        val hubLookup = hubLookup(result.content)
        return PageResponse(
            items = result.content.map { lane ->
                HubLaneResponse.from(lane, hubLookup.getValue(lane.fromHubId), hubLookup.getValue(lane.toHubId))
            },
            page = result.number + 1,
            pageSize = result.size,
            totalItems = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): HubLaneResponse {
        val lane = find(id)
        val endpoints = getEndpoints(lane.fromHubId, lane.toHubId)
        return HubLaneResponse.from(lane, endpoints.first, endpoints.second)
    }

    @Transactional
    fun create(request: CreateHubLaneRequest, actorUserId: UUID): HubLaneResponse {
        val code = request.code.trim().uppercase()
        if (lanes.existsByCodeIgnoreCase(code)) {
            throw ConflictException("lane_code_exists", "Hub lane code already exists.")
        }
        validateOperationalDetails(request.estimatedTransitMinutes, request.distanceKm, request.maxShipmentWeightKg)
        val (fromHub, toHub) = getEndpoints(request.fromHubId, request.toHubId)
        validateDifferentEndpoints(fromHub.id, toHub.id)

        val lane = lanes.save(
            HubLane(
                code = code,
                fromHubId = fromHub.id,
                toHubId = toHub.id,
                transportMode = request.transportMode,
                estimatedTransitMinutes = request.estimatedTransitMinutes,
                distanceKm = request.distanceKm,
                maxShipmentWeightKg = request.maxShipmentWeightKg,
                status = HubLaneStatus.INACTIVE,
            ),
        )
        val response = HubLaneResponse.from(lane, fromHub, toHub)
        audit.record(NetworkAuditEntityType.LANE, lane.id, NetworkAuditAction.CREATED, actorUserId, after = response)
        return response
    }

    @Transactional
    fun update(id: UUID, request: UpdateHubLaneRequest, actorUserId: UUID): HubLaneResponse {
        val lane = find(id)
        val beforeEndpoints = getEndpoints(lane.fromHubId, lane.toHubId)
        val before = HubLaneResponse.from(lane, beforeEndpoints.first, beforeEndpoints.second)
        validateOperationalDetails(request.estimatedTransitMinutes, request.distanceKm, request.maxShipmentWeightKg)
        val (fromHub, toHub) = getEndpoints(request.fromHubId, request.toHubId)
        validateDifferentEndpoints(fromHub.id, toHub.id)

        val topologyChanged = lane.fromHubId != request.fromHubId ||
            lane.toHubId != request.toHubId || lane.transportMode != request.transportMode
        if (topologyChanged && lane.status == HubLaneStatus.ACTIVE) {
            throw ConflictException(
                "active_lane_topology_locked",
                "Deactivate the lane before changing its endpoints or transport mode.",
            )
        }

        lane.update(
            request.fromHubId,
            request.toHubId,
            request.transportMode,
            request.estimatedTransitMinutes,
            request.distanceKm,
            request.maxShipmentWeightKg,
        )
        val saved = lanes.save(lane)
        val response = HubLaneResponse.from(saved, fromHub, toHub)
        audit.record(NetworkAuditEntityType.LANE, id, NetworkAuditAction.UPDATED, actorUserId, before, response)
        return response
    }

    @Transactional
    fun updateStatus(id: UUID, request: UpdateHubLaneStatusRequest, actorUserId: UUID): HubLaneResponse {
        val lane = find(id)
        val (fromHub, toHub) = getEndpoints(lane.fromHubId, lane.toHubId)
        val before = HubLaneResponse.from(lane, fromHub, toHub)

        if (request.active) {
            if (fromHub.status != HubStatus.ACTIVE || toHub.status != HubStatus.ACTIVE) {
                throw ConflictException("lane_hub_inactive", "Both endpoint hubs must be active before activating a lane.")
            }
            val activeSchedules = schedules.findAllByLaneIdAndStatusOrderByDepartureTimeAsc(
                id,
                LaneScheduleStatus.ACTIVE,
            )
            if (activeSchedules.none(LaneScheduleService::canOperateFromToday)) {
                throw ConflictException(
                    "lane_schedule_required",
                    "Add at least one active current or future schedule before activating the lane.",
                )
            }
        }

        lane.setActive(request.active)
        val saved = lanes.save(lane)
        val response = HubLaneResponse.from(saved, fromHub, toHub)
        audit.record(
            NetworkAuditEntityType.LANE,
            id,
            if (request.active) NetworkAuditAction.ACTIVATED else NetworkAuditAction.DEACTIVATED,
            actorUserId,
            before,
            response,
            request.reason,
        )
        return response
    }

    fun find(id: UUID): HubLane = lanes.findById(id).orElseThrow {
        NotFoundException("lane_not_found", "Hub lane was not found.")
    }

    private fun getEndpoints(fromHubId: UUID, toHubId: UUID): Pair<Hub, Hub> {
        val lookup = hubs.findAllById(listOf(fromHubId, toHubId)).associateBy { it.id }
        val from = lookup[fromHubId] ?: throw NotFoundException("hub_not_found", "Origin hub was not found.")
        val to = lookup[toHubId] ?: throw NotFoundException("hub_not_found", "Destination hub was not found.")
        return from to to
    }

    private fun hubLookup(items: Collection<HubLane>): Map<UUID, Hub> {
        val ids = items.flatMap { listOf(it.fromHubId, it.toHubId) }.toSet()
        return hubs.findAllById(ids).associateBy { it.id }
    }

    private fun validateDifferentEndpoints(fromHubId: UUID, toHubId: UUID) {
        if (fromHubId == toHubId) {
            throw InvalidNetworkRequestException("lane_same_hub", "A hub lane must connect two different hubs.")
        }
    }

    private fun validateOperationalDetails(
        transitMinutes: Int,
        distanceKm: java.math.BigDecimal?,
        maxShipmentWeightKg: java.math.BigDecimal,
    ) {
        if (transitMinutes <= 0) {
            throw InvalidNetworkRequestException("invalid_transit_time", "Estimated transit time must be positive.")
        }
        if (distanceKm != null && distanceKm.signum() <= 0) {
            throw InvalidNetworkRequestException("invalid_distance", "Distance must be positive when supplied.")
        }
        if (maxShipmentWeightKg.signum() <= 0) {
            throw InvalidNetworkRequestException("invalid_lane_weight", "Maximum shipment weight must be positive.")
        }
    }
}
