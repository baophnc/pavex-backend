package com.pavex.network.service

import com.pavex.network.dto.capacity.CapacityReservationBatchStatus
import com.pavex.network.dto.capacity.CapacityReservationLegResponse
import com.pavex.network.dto.capacity.CapacityReservationResponse
import com.pavex.network.dto.capacity.LaneCapacityResponse
import com.pavex.network.dto.capacity.LaneDepartureCapacityResponse
import com.pavex.network.dto.capacity.ReserveLaneCapacityRequest
import com.pavex.network.dto.routing.RoutePlanSource
import com.pavex.network.entity.CapacityRouteSource
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.LaneCapacityReservation
import com.pavex.network.entity.LaneCapacityReservationStatus
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.entity.NetworkAuditAction
import com.pavex.network.entity.NetworkAuditEntityType
import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.LaneCapacityReservationRepository
import com.pavex.network.repository.LaneScheduleRepository
import com.pavex.network.repository.ReservationIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

@Service
class NetworkCapacityService(
    private val planner: NetworkRoutePlanner,
    private val lanes: HubLaneRepository,
    private val schedules: LaneScheduleRepository,
    private val reservations: LaneCapacityReservationRepository,
    private val audit: NetworkAuditService,
    private val transactionLocks: NetworkTransactionLockService,
    @Value("\${network.capacity.reservation-retries:8}")
    private val reservationRetries: Int,
) {
    @Transactional(readOnly = true)
    fun getReservation(shipmentId: UUID, routeRevision: Int): CapacityReservationResponse {
        validateIdentity(shipmentId, routeRevision)
        val items = reservations.findAllByShipmentIdAndRouteRevisionOrderByLegSequenceAsc(shipmentId, routeRevision)
        if (items.isEmpty()) throw reservationNotFound()
        return toResponse(items)
    }

    /**
     * @param confirmationDeadlineAt when set, the reservation is released automatically unless
     * it is confirmed (see [confirm]) before this instant. Admin reservations pass null.
     */
    @Transactional
    fun reserve(
        request: ReserveLaneCapacityRequest,
        actorUserId: UUID,
        confirmationDeadlineAt: Instant? = null,
    ): CapacityReservationResponse {
        validateIdentity(request.shipmentId, request.routeRevision)
        if (request.shipmentWeightKg.signum() <= 0) {
            throw InvalidNetworkRequestException("invalid_shipment_weight", "Shipment weight must be positive.")
        }
        if (reservationRetries !in 1..50) {
            throw IllegalStateException("Network capacity retry configuration is invalid.")
        }

        transactionLocks.lock("shipment:${request.shipmentId}:${request.routeRevision}")
        val existing = reservations.findAllByShipmentIdAndRouteRevisionOrderByLegSequenceAsc(
            request.shipmentId,
            request.routeRevision,
        )
        if (existing.isNotEmpty()) return toResponse(existing)

        val excluded = linkedSetOf<DepartureKey>()
        repeat(reservationRetries) {
            val plan = try {
                planner.plan(
                    originProvinceCode = request.originProvinceCode,
                    originWardCode = request.originWardCode,
                    destinationProvinceCode = request.destinationProvinceCode,
                    destinationWardCode = request.destinationWardCode,
                    shipmentWeightKg = request.shipmentWeightKg,
                    readyAt = request.readyAt,
                    mode = request.mode,
                    excludedDepartures = excluded,
                )
            } catch (exception: NotFoundException) {
                if (exception.code !in setOf("route_not_found", "route_template_not_available")) throw exception
                throw ConflictException(
                    "network_capacity_unavailable",
                    "No currently available transport departure can reserve enough capacity for this shipment.",
                )
            }

            if (plan.legs.isEmpty()) {
                return CapacityReservationResponse(
                    batchId = null,
                    shipmentId = request.shipmentId,
                    routeRevision = request.routeRevision,
                    shipmentWeightKg = request.shipmentWeightKg,
                    estimatedDeliveryAt = plan.arrivalAt,
                    source = RoutePlanSource.LOCAL,
                    status = CapacityReservationBatchStatus.NOT_REQUIRED,
                    legs = emptyList(),
                )
            }

            val keys = plan.legs.map { DepartureKey(it.scheduleId, it.serviceDate) }.distinct()
            transactionLocks.lockAll(plan.legs.map { "lane:${it.laneId}" })
            transactionLocks.lockAll(plan.legs.map { "schedule:${it.scheduleId}" })
            transactionLocks.lockAll(keys.map { "departure:${it.scheduleId}:${it.serviceDate}" })

            val scheduleLookup = schedules.findAllById(plan.legs.map { it.scheduleId }.distinct()).associateBy { it.id }
            val laneLookup = lanes.findAllById(plan.legs.map { it.laneId }.distinct()).associateBy { it.id }
            val currentAllocated = allocatedByDeparture(keys)
            val newlyFull = linkedSetOf<DepartureKey>()

            plan.legs.forEach { leg ->
                val schedule = scheduleLookup[leg.scheduleId]
                    ?: throw ConflictException("capacity_schedule_changed", "A selected lane schedule changed before capacity could be reserved.")
                val lane = laneLookup[leg.laneId]
                    ?: throw ConflictException("capacity_lane_changed", "A selected lane changed before capacity could be reserved.")
                validateSelectedDeparture(
                    leg,
                    lane,
                    request.shipmentWeightKg,
                    schedule,
                )

                val key = DepartureKey(schedule.id, leg.serviceDate)
                val allocated = currentAllocated[key] ?: BigDecimal.ZERO
                if (schedule.capacityKg.subtract(allocated) < request.shipmentWeightKg) newlyFull += key
            }

            if (newlyFull.isNotEmpty()) {
                excluded += newlyFull
                return@repeat
            }

            val batchId = UUID.randomUUID()
            val routeSource = when (plan.source) {
                RoutePlanSource.ROUTE_TEMPLATE -> CapacityRouteSource.ROUTE_TEMPLATE
                RoutePlanSource.GRAPH -> CapacityRouteSource.GRAPH
                RoutePlanSource.LOCAL -> error("Local plans do not have capacity legs.")
            }
            val items = plan.legs.map { leg ->
                val schedule = scheduleLookup.getValue(leg.scheduleId)
                LaneCapacityReservation(
                    batchId = batchId,
                    shipmentId = request.shipmentId,
                    routeRevision = request.routeRevision,
                    laneId = leg.laneId,
                    scheduleId = leg.scheduleId,
                    legSequence = leg.sequence,
                    serviceDate = leg.serviceDate,
                    departureAt = leg.departureAt,
                    arrivalAt = leg.arrivalAt,
                    reservedWeightKg = request.shipmentWeightKg,
                    departureCapacityKg = schedule.capacityKg,
                    routeSource = routeSource,
                    routeTemplateId = plan.routeTemplateId,
                    routeTemplateCode = plan.routeTemplateCode,
                    routeTemplateRevision = plan.routeTemplateRevision,
                    confirmationDeadlineAt = confirmationDeadlineAt,
                )
            }
            val saved = reservations.saveAll(items).sortedBy { it.legSequence }
            val response = toResponse(saved)
            audit.record(
                NetworkAuditEntityType.CAPACITY_RESERVATION,
                batchId,
                NetworkAuditAction.RESERVED,
                actorUserId,
                after = response,
            )
            return response
        }

        throw ConflictException(
            "network_capacity_race",
            "Capacity changed repeatedly while reserving this route. Retry the reservation.",
        )
    }

    @Transactional
    fun release(
        shipmentId: UUID,
        routeRevision: Int,
        actorUserId: UUID,
        reason: String?,
    ): CapacityReservationResponse {
        validateIdentity(shipmentId, routeRevision)
        transactionLocks.lock("shipment:$shipmentId:$routeRevision")
        val items = reservations.findAllByShipmentIdAndRouteRevisionOrderByLegSequenceAsc(shipmentId, routeRevision)
        if (items.isEmpty()) throw reservationNotFound()

        val before = toResponse(items)
        items.filter { it.status == LaneCapacityReservationStatus.RESERVED }.forEach(LaneCapacityReservation::release)
        val saved = reservations.saveAll(items).sortedBy { it.legSequence }
        val response = toResponse(saved)
        audit.record(
            NetworkAuditEntityType.CAPACITY_RESERVATION,
            saved.first().batchId,
            NetworkAuditAction.RELEASED,
            actorUserId,
            before,
            response,
            reason,
        )
        return response
    }

    /**
     * Marks a shipment reservation as owned by a persisted shipment. Idempotent.
     */
    @Transactional
    fun confirm(shipmentId: UUID, routeRevision: Int, actorUserId: UUID): CapacityReservationResponse {
        validateIdentity(shipmentId, routeRevision)
        transactionLocks.lock("shipment:$shipmentId:$routeRevision")
        val items = reservations.findAllByShipmentIdAndRouteRevisionOrderByLegSequenceAsc(shipmentId, routeRevision)
        if (items.isEmpty()) throw reservationNotFound()
        if (items.all { it.confirmedAt != null }) return toResponse(items)
        if (items.any { it.status == LaneCapacityReservationStatus.RELEASED }) {
            throw ConflictException(
                "capacity_already_released",
                "The capacity reservation was released before the shipment confirmed it.",
            )
        }

        val before = toResponse(items)
        val now = Instant.now()
        items.forEach { it.confirm(now) }
        val saved = reservations.saveAll(items).sortedBy { it.legSequence }
        val response = toResponse(saved)
        audit.record(
            NetworkAuditEntityType.CAPACITY_RESERVATION,
            saved.first().batchId,
            NetworkAuditAction.UPDATED,
            actorUserId,
            before,
            response,
            reason = "Confirmed by persisted shipment",
        )
        return response
    }

    @Transactional(readOnly = true)
    fun findExpiredUnconfirmedReservations(now: Instant, limit: Int): List<ReservationIdentity> =
        reservations.findExpiredUnconfirmedReservations(now, PageRequest.of(0, limit))

    /**
     * Releases one expired, never-confirmed shipment reservation. Re-checks every leg under the
     * shipment lock so a confirmation or consumption racing with the expiry job always wins.
     *
     * @return true when capacity was released.
     */
    @Transactional
    fun releaseIfExpiredUnconfirmed(shipmentId: UUID, routeRevision: Int, now: Instant): Boolean {
        transactionLocks.lock("shipment:$shipmentId:$routeRevision")
        val items = reservations.findAllByShipmentIdAndRouteRevisionOrderByLegSequenceAsc(shipmentId, routeRevision)
        if (items.isEmpty() || !items.all { it.isExpiredUnconfirmed(now) }) return false

        val before = toResponse(items)
        items.forEach(LaneCapacityReservation::release)
        val saved = reservations.saveAll(items).sortedBy { it.legSequence }
        audit.record(
            NetworkAuditEntityType.CAPACITY_RESERVATION,
            saved.first().batchId,
            NetworkAuditAction.RELEASED,
            SYSTEM_ACTOR_ID,
            before,
            toResponse(saved),
            reason = "Shipment never confirmed the reservation before its deadline",
        )
        return true
    }

    @Transactional
    fun consumeLeg(
        shipmentId: UUID,
        routeRevision: Int,
        legSequence: Int,
        actorUserId: UUID,
    ): CapacityReservationResponse {
        validateIdentity(shipmentId, routeRevision)
        if (legSequence <= 0) {
            throw InvalidNetworkRequestException("invalid_leg_sequence", "Leg sequence must be positive.")
        }
        transactionLocks.lock("shipment:$shipmentId:$routeRevision")
        val items = reservations.findAllByShipmentIdAndRouteRevisionOrderByLegSequenceAsc(shipmentId, routeRevision)
        if (items.isEmpty()) throw reservationNotFound()
        val target = items.singleOrNull { it.legSequence == legSequence }
            ?: throw NotFoundException("capacity_leg_not_found", "Capacity reservation leg was not found.")
        if (target.status == LaneCapacityReservationStatus.RELEASED) {
            throw ConflictException("capacity_already_released", "Released lane capacity cannot be consumed.")
        }
        if (items.any { it.legSequence < legSequence && it.status != LaneCapacityReservationStatus.CONSUMED }) {
            throw ConflictException(
                "capacity_leg_out_of_order",
                "Consume earlier route legs before consuming this capacity reservation.",
            )
        }

        val before = toResponse(items)
        // Consumption proves the shipment exists, so the batch can never expire afterwards.
        val now = Instant.now()
        items.forEach { it.confirm(now) }
        target.consume()
        val saved = reservations.saveAll(items).sortedBy { it.legSequence }
        val response = toResponse(saved)
        audit.record(
            NetworkAuditEntityType.CAPACITY_RESERVATION,
            target.batchId,
            NetworkAuditAction.CONSUMED,
            actorUserId,
            before,
            response,
            reason = "Consumed route leg $legSequence",
        )
        return response
    }

    @Transactional(readOnly = true)
    fun getLaneCapacity(laneId: UUID, serviceDate: LocalDate): LaneCapacityResponse {
        val lane = lanes.findById(laneId).orElseThrow {
            NotFoundException("lane_not_found", "Hub lane was not found.")
        }
        val operatingSchedules = schedules.findAllByLaneIdAndStatusOrderByDepartureTimeAsc(
            laneId,
            LaneScheduleStatus.ACTIVE,
        ).filter { it.operatesOn(serviceDate) }
        if (operatingSchedules.isEmpty()) {
            return LaneCapacityResponse(
                laneId = lane.id,
                laneCode = lane.code,
                serviceDate = serviceDate,
                totalCapacityKg = BigDecimal.ZERO,
                allocatedCapacityKg = BigDecimal.ZERO,
                remainingCapacityKg = BigDecimal.ZERO,
                departures = emptyList(),
            )
        }

        val allocated = reservations.findAllByScheduleIdInAndServiceDateAndStatusIn(
            operatingSchedules.map { it.id },
            serviceDate,
            ALLOCATED_STATUSES,
        ).groupBy { it.scheduleId }
            .mapValues { (_, items) -> items.fold(BigDecimal.ZERO) { total, item -> total.add(item.reservedWeightKg) } }

        val departureResponses = operatingSchedules.map { schedule ->
            val allocatedWeight = allocated[schedule.id] ?: BigDecimal.ZERO
            LaneDepartureCapacityResponse(
                scheduleId = schedule.id,
                departureTime = schedule.departureTime,
                serviceDate = serviceDate,
                capacityKg = schedule.capacityKg,
                allocatedCapacityKg = allocatedWeight,
                remainingCapacityKg = schedule.capacityKg.subtract(allocatedWeight).max(BigDecimal.ZERO),
            )
        }
        val totalCapacity = departureResponses.fold(BigDecimal.ZERO) { total, item -> total.add(item.capacityKg) }
        val totalAllocated = departureResponses.fold(BigDecimal.ZERO) { total, item -> total.add(item.allocatedCapacityKg) }
        return LaneCapacityResponse(
            laneId = lane.id,
            laneCode = lane.code,
            serviceDate = serviceDate,
            totalCapacityKg = totalCapacity,
            allocatedCapacityKg = totalAllocated,
            remainingCapacityKg = totalCapacity.subtract(totalAllocated).max(BigDecimal.ZERO),
            departures = departureResponses,
        )
    }

    private fun validateSelectedDeparture(
        leg: com.pavex.network.dto.routing.RoutePlanLegResponse,
        lane: com.pavex.network.entity.HubLane,
        shipmentWeightKg: BigDecimal,
        schedule: com.pavex.network.entity.LaneSchedule,
    ) {
        if (lane.status != HubLaneStatus.ACTIVE ||
            lane.fromHubId != leg.fromHubId ||
            lane.toHubId != leg.toHubId ||
            lane.transportMode != leg.transportMode ||
            schedule.status != LaneScheduleStatus.ACTIVE ||
            schedule.laneId != leg.laneId
        ) {
            throw ConflictException("capacity_route_changed", "A selected route changed before capacity could be reserved.")
        }
        if (lane.maxShipmentWeightKg < shipmentWeightKg || schedule.capacityKg < shipmentWeightKg) {
            throw ConflictException("capacity_route_changed", "A selected departure can no longer carry this shipment weight.")
        }
        if (!schedule.operatesOn(leg.serviceDate)) {
            throw ConflictException("capacity_schedule_changed", "The selected schedule no longer operates on the planned date.")
        }
        val expectedDeparture = ZonedDateTime.of(
            leg.serviceDate,
            schedule.departureTime,
            LaneScheduleCalculator.VIETNAM_ZONE,
        ).toInstant()
        val expectedArrival = expectedDeparture.plusSeconds(lane.estimatedTransitMinutes.toLong() * 60)
        if (expectedDeparture != leg.departureAt || expectedArrival != leg.arrivalAt) {
            throw ConflictException("capacity_schedule_changed", "The selected departure changed before reservation.")
        }
    }

    private fun allocatedByDeparture(keys: Collection<DepartureKey>): Map<DepartureKey, BigDecimal> {
        if (keys.isEmpty()) return emptyMap()
        val scheduleIds = keys.map { it.scheduleId }.distinct()
        val from = keys.minOf { it.serviceDate }
        val to = keys.maxOf { it.serviceDate }
        val selected = keys.toSet()
        return reservations.findAllByScheduleIdInAndServiceDateBetweenAndStatusIn(
            scheduleIds,
            from,
            to,
            ALLOCATED_STATUSES,
        ).asSequence()
            .filter { DepartureKey(it.scheduleId, it.serviceDate) in selected }
            .groupBy { DepartureKey(it.scheduleId, it.serviceDate) }
            .mapValues { (_, items) -> items.fold(BigDecimal.ZERO) { total, item -> total.add(item.reservedWeightKg) } }
    }

    private fun toResponse(items: List<LaneCapacityReservation>): CapacityReservationResponse {
        val ordered = items.sortedBy { it.legSequence }
        val first = ordered.first()
        val laneCodes = lanes.findAllById(ordered.map { it.laneId }.distinct()).associate { it.id to it.code }
        val hasReserved = ordered.any { it.status == LaneCapacityReservationStatus.RESERVED }
        val hasConsumed = ordered.any { it.status == LaneCapacityReservationStatus.CONSUMED }
        val hasReleased = ordered.any { it.status == LaneCapacityReservationStatus.RELEASED }
        val batchStatus = when {
            hasReserved && hasConsumed -> CapacityReservationBatchStatus.IN_TRANSIT
            hasReserved -> CapacityReservationBatchStatus.RESERVED
            hasConsumed && hasReleased -> CapacityReservationBatchStatus.PARTIALLY_CONSUMED
            hasConsumed -> CapacityReservationBatchStatus.CONSUMED
            else -> CapacityReservationBatchStatus.RELEASED
        }
        return CapacityReservationResponse(
            batchId = first.batchId,
            shipmentId = first.shipmentId,
            routeRevision = first.routeRevision,
            shipmentWeightKg = first.reservedWeightKg,
            estimatedDeliveryAt = ordered.maxOf { it.arrivalAt },
            source = RoutePlanSource.valueOf(first.routeSource.name),
            routeTemplateId = first.routeTemplateId,
            routeTemplateCode = first.routeTemplateCode,
            routeTemplateRevision = first.routeTemplateRevision,
            status = batchStatus,
            legs = ordered.map { item ->
                CapacityReservationLegResponse(
                    reservationId = item.id,
                    sequence = item.legSequence,
                    laneId = item.laneId,
                    laneCode = laneCodes[item.laneId] ?: "UNKNOWN",
                    scheduleId = item.scheduleId,
                    serviceDate = item.serviceDate,
                    departureAt = item.departureAt,
                    arrivalAt = item.arrivalAt,
                    departureCapacityKg = item.departureCapacityKg,
                    reservedWeightKg = item.reservedWeightKg,
                    status = item.status,
                )
            },
        )
    }

    private fun reservationNotFound() = NotFoundException(
        "capacity_reservation_not_found",
        "Capacity reservation was not found.",
    )

    private fun validateIdentity(shipmentId: UUID, routeRevision: Int) {
        if (shipmentId == UUID(0, 0)) {
            throw InvalidNetworkRequestException("shipment_id_required", "Shipment identifier is required.")
        }
        if (routeRevision <= 0) {
            throw InvalidNetworkRequestException("invalid_route_revision", "Route revision must be positive.")
        }
    }

    companion object {
        /** Audit actor for automatic, non-user actions. */
        val SYSTEM_ACTOR_ID: UUID = UUID(0, 0)

        private val ALLOCATED_STATUSES = listOf(
            LaneCapacityReservationStatus.RESERVED,
            LaneCapacityReservationStatus.CONSUMED,
        )
    }
}
