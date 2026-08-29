package com.pavex.network.seed

import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubLane
import com.pavex.network.entity.HubLaneStatus
import com.pavex.network.entity.LaneSchedule
import com.pavex.network.entity.LaneScheduleStatus
import com.pavex.network.entity.RouteTemplate
import com.pavex.network.entity.RouteTemplateLeg
import com.pavex.network.entity.RouteTemplateStatus
import com.pavex.network.repository.HubLaneRepository
import com.pavex.network.repository.HubRepository
import com.pavex.network.repository.LaneScheduleRepository
import com.pavex.network.repository.RouteTemplateLegRepository
import com.pavex.network.repository.RouteTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@Component
@Order(200)
@ConditionalOnProperty(prefix = "network.seed.demo", name = ["enabled"], havingValue = "true")
class NetworkDemoTransportSeeder(
    private val hubRepository: HubRepository,
    private val laneRepository: HubLaneRepository,
    private val scheduleRepository: LaneScheduleRepository,
    private val routeTemplateRepository: RouteTemplateRepository,
    private val routeTemplateLegRepository: RouteTemplateLegRepository,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val laneSeeds = NetworkDemoTransportData.lanes()
        val hubs = loadRequiredHubs(laneSeeds)
        val lanes = ensureLanes(laneSeeds, hubs)
        val createdSchedules = ensureSchedules(laneSeeds, lanes)
        val templateSeeds = NetworkDemoTransportData.routeTemplates()
        val createdTemplates = ensureTemplates(templateSeeds, hubs, lanes)

        logger.info(
            "Network demo transport ready: {} active lanes, {} configured schedules ({} new), {} route templates ({} new)",
            laneSeeds.size,
            laneSeeds.sumOf { it.schedules.size },
            createdSchedules,
            templateSeeds.size,
            createdTemplates,
        )
    }

    private fun loadRequiredHubs(laneSeeds: List<DemoLaneSeed>): Map<String, Hub> {
        val requiredCodes = laneSeeds
            .flatMap { listOf(it.fromHubCode, it.toHubCode) }
            .toSet()
        return requiredCodes.associateWith { code ->
            requireNotNull(hubRepository.findByCodeIgnoreCase(code)) {
                "Missing demo geography hub $code. Apply/start Network demo geography before transport seed."
            }
        }
    }

    private fun ensureLanes(
        seeds: List<DemoLaneSeed>,
        hubs: Map<String, Hub>,
    ): Map<String, HubLane> = seeds.associate { seed ->
        val existing = laneRepository.findByCodeIgnoreCase(seed.code)
        val lane = existing ?: laneRepository.save(
            HubLane(
                id = stableUuid("lane:${seed.code}"),
                code = seed.code,
                fromHubId = requireNotNull(hubs[seed.fromHubCode]).id,
                toHubId = requireNotNull(hubs[seed.toHubCode]).id,
                transportMode = seed.transportMode,
                estimatedTransitMinutes = seed.estimatedTransitMinutes,
                distanceKm = seed.distanceKm,
                maxShipmentWeightKg = seed.maxShipmentWeightKg,
                status = HubLaneStatus.ACTIVE,
            ),
        )

        if (existing != null && existing.status != HubLaneStatus.ACTIVE) {
            logger.warn("Demo seed keeps operator lane {} in {} status", existing.code, existing.status)
        }
        seed.code to lane
    }.also { laneRepository.flush() }

    private fun ensureSchedules(
        seeds: List<DemoLaneSeed>,
        lanes: Map<String, HubLane>,
    ): Int {
        var created = 0

        seeds.forEach { laneSeed ->
            val lane = requireNotNull(lanes[laneSeed.code])
            val current = scheduleRepository.findAllByLaneIdOrderByDepartureTimeAsc(lane.id)
            val deterministicIds = laneSeed.schedules.indices
                .map { stableUuid("schedule:${laneSeed.code}:$it") }
                .toSet()

            val hasOperatorSchedulesOnly = current.isNotEmpty() && current.none { it.id in deterministicIds }
            if (hasOperatorSchedulesOnly) {
                logger.warn(
                    "Demo seed keeps {} operator schedule(s) on lane {} and skips demo schedules for that lane",
                    current.size,
                    lane.code,
                )
                return@forEach
            }

            laneSeed.schedules.forEachIndexed { index, seed ->
                val id = stableUuid("schedule:${laneSeed.code}:$index")
                if (scheduleRepository.existsById(id)) return@forEachIndexed

                scheduleRepository.save(
                    LaneSchedule.create(
                        laneId = lane.id,
                        departureTime = seed.departureTime,
                        daysOfWeek = seed.daysOfWeek,
                        effectiveFrom = NetworkDemoTransportData.effectiveFrom,
                        effectiveTo = null,
                        active = true,
                        capacityKg = seed.capacityKg,
                    ).also { it.id = id },
                )
                created += 1
            }
        }
        scheduleRepository.flush()
        return created
    }

    private fun ensureTemplates(
        seeds: List<DemoRouteTemplateSeed>,
        hubs: Map<String, Hub>,
        lanes: Map<String, HubLane>,
    ): Int {
        var created = 0

        seeds.forEach { seed ->
            val existing = routeTemplateRepository.findByCodeIgnoreCase(seed.code)
            if (existing != null) {
                logger.debug("Demo route template {} already exists; keeping operator configuration", seed.code)
                return@forEach
            }

            val path = seed.laneCodes.map { code -> requireNotNull(lanes[code]) { "Missing demo lane $code" } }
            val expectedOrigin = requireNotNull(hubs[seed.originHubCode]).id
            val expectedDestination = requireNotNull(hubs[seed.destinationHubCode]).id
            val connected = path.first().fromHubId == expectedOrigin &&
                path.last().toHubId == expectedDestination &&
                path.zipWithNext().all { (left, right) -> left.toHubId == right.fromHubId }
            val operational = path.all { lane ->
                lane.status == HubLaneStatus.ACTIVE &&
                    scheduleRepository.findAllByLaneIdAndStatusOrderByDepartureTimeAsc(
                        lane.id,
                        LaneScheduleStatus.ACTIVE,
                    ).isNotEmpty()
            }
            if (!connected || !operational) {
                logger.warn(
                    "Demo route template {} was not created because operator lane configuration is disconnected or unavailable",
                    seed.code,
                )
                return@forEach
            }

            val now = Instant.now()
            val template = routeTemplateRepository.save(
                RouteTemplate(
                    id = stableUuid("template:${seed.code}"),
                    code = seed.code,
                    name = seed.name,
                    description = seed.description,
                    originHubId = requireNotNull(hubs[seed.originHubCode]).id,
                    destinationHubId = requireNotNull(hubs[seed.destinationHubCode]).id,
                    priority = seed.priority,
                    status = RouteTemplateStatus.ACTIVE,
                    revision = 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            routeTemplateRepository.flush()

            routeTemplateLegRepository.saveAll(
                seed.laneCodes.mapIndexed { index, laneCode ->
                    RouteTemplateLeg(
                        id = stableUuid("template-leg:${seed.code}:${index + 1}"),
                        routeTemplateId = template.id,
                        sequence = index + 1,
                        laneId = requireNotNull(lanes[laneCode]).id,
                    )
                },
            )
            created += 1
        }
        routeTemplateLegRepository.flush()
        return created
    }

    private fun stableUuid(key: String): UUID =
        UUID.nameUUIDFromBytes("pavex-network-demo:$key".toByteArray(StandardCharsets.UTF_8))
}
