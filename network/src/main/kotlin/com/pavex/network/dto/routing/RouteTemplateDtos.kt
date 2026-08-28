package com.pavex.network.dto.routing

import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.RouteTemplate
import com.pavex.network.entity.RouteTemplateStatus
import com.pavex.network.entity.TransportMode
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateRouteTemplateRequest(
    @field:NotBlank
    @field:Size(max = 80)
    @field:Pattern(regexp = "^[A-Za-z0-9_-]+$")
    val code: String,
    @field:NotBlank @field:Size(max = 160)
    val name: String,
    @field:Size(max = 500)
    val description: String? = null,
    val originHubId: UUID,
    val destinationHubId: UUID,
    @field:Min(1) @field:Max(10000)
    val priority: Int = 100,
    @field:Size(min = 1, max = 30)
    val laneIds: List<UUID>,
)

data class UpdateRouteTemplateRequest(
    @field:NotBlank @field:Size(max = 160)
    val name: String,
    @field:Size(max = 500)
    val description: String? = null,
    val originHubId: UUID,
    val destinationHubId: UUID,
    @field:Min(1) @field:Max(10000)
    val priority: Int,
    @field:Size(min = 1, max = 30)
    val laneIds: List<UUID>,
)

data class UpdateRouteTemplateStatusRequest(
    val active: Boolean,
    @field:Size(max = 500) val reason: String? = null,
)

data class RouteTemplateResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String?,
    val originHubId: UUID,
    val originHubCode: String,
    val destinationHubId: UUID,
    val destinationHubCode: String,
    val priority: Int,
    val status: RouteTemplateStatus,
    val revision: Int,
    val estimatedTransitMinutes: Int,
    val totalDistanceKm: BigDecimal?,
    val legs: List<RouteTemplateLegResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            template: RouteTemplate,
            path: List<HubLane>,
            hubs: Map<UUID, Hub>,
        ): RouteTemplateResponse {
            val distance = if (path.all { it.distanceKm != null }) {
                path.fold(BigDecimal.ZERO) { total, lane -> total.add(requireNotNull(lane.distanceKm)) }
            } else {
                null
            }
            return RouteTemplateResponse(
                id = template.id,
                code = template.code,
                name = template.name,
                description = template.description,
                originHubId = template.originHubId,
                originHubCode = hubs.getValue(template.originHubId).code,
                destinationHubId = template.destinationHubId,
                destinationHubCode = hubs.getValue(template.destinationHubId).code,
                priority = template.priority,
                status = template.status,
                revision = template.revision,
                estimatedTransitMinutes = path.sumOf { it.estimatedTransitMinutes },
                totalDistanceKm = distance,
                legs = path.mapIndexed { index, lane ->
                    RouteTemplateLegResponse(
                        sequence = index + 1,
                        laneId = lane.id,
                        laneCode = lane.code,
                        fromHubId = lane.fromHubId,
                        fromHubCode = hubs.getValue(lane.fromHubId).code,
                        toHubId = lane.toHubId,
                        toHubCode = hubs.getValue(lane.toHubId).code,
                        transportMode = lane.transportMode,
                        estimatedTransitMinutes = lane.estimatedTransitMinutes,
                        distanceKm = lane.distanceKm,
                        maxShipmentWeightKg = lane.maxShipmentWeightKg,
                        laneStatus = lane.status,
                    )
                },
                createdAt = template.createdAt,
                updatedAt = template.updatedAt,
            )
        }
    }
}

data class RouteTemplateLegResponse(
    val sequence: Int,
    val laneId: UUID,
    val laneCode: String,
    val fromHubId: UUID,
    val fromHubCode: String,
    val toHubId: UUID,
    val toHubCode: String,
    val transportMode: TransportMode,
    val estimatedTransitMinutes: Int,
    val distanceKm: BigDecimal?,
    val maxShipmentWeightKg: BigDecimal,
    val laneStatus: HubLaneStatus,
)


data class RouteTemplateJourneyResponse(
    val routeTemplateId: UUID,
    val routeTemplateCode: String,
    val routeTemplateName: String,
    val priority: Int,
    val revision: Int,
    val originHubId: UUID,
    val originHubCode: String,
    val destinationHubId: UUID,
    val destinationHubCode: String,
    val shipmentWeightKg: BigDecimal,
    val readyAt: Instant,
    val arrivalAt: Instant,
    val totalJourneyMinutes: Long,
    val inTransitMinutes: Int,
    val totalDistanceKm: BigDecimal?,
    val legs: List<RoutePlanLegResponse>,
)
