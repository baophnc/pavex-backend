package com.pavex.network.seed

import com.pavex.network.entity.TransportMode
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class DemoScheduleSeed(
    val departureTime: LocalTime,
    val daysOfWeek: Set<DayOfWeek>,
    val capacityKg: BigDecimal,
)

data class DemoLaneSeed(
    val code: String,
    val fromHubCode: String,
    val toHubCode: String,
    val transportMode: TransportMode,
    val estimatedTransitMinutes: Int,
    val distanceKm: BigDecimal,
    val maxShipmentWeightKg: BigDecimal,
    val schedules: List<DemoScheduleSeed>,
)

data class DemoRouteTemplateSeed(
    val code: String,
    val name: String,
    val description: String,
    val originHubCode: String,
    val destinationHubCode: String,
    val priority: Int,
    val laneCodes: List<String>,
)

object NetworkDemoTransportData {
    val effectiveFrom: LocalDate = LocalDate.of(2026, 1, 1)
    val everyDay: Set<DayOfWeek> = DayOfWeek.entries.toSet()

    private data class Metro(
        val prefix: String,
        val gatewayCode: String,
        val majorHubCode: String,
        val name: String,
    )

    private val metros = listOf(
        Metro("HN", "HN-GW", "HN-HOANKIEM", "Hà Nội"),
        Metro("DN", "DN-GW", "DN-HAICHAU", "Đà Nẵng"),
        Metro("HCM", "HCM-GW", "HCM-SAIGON", "Hồ Chí Minh"),
    )

    fun lanes(): List<DemoLaneSeed> = buildList {
        NetworkDemoGeographyData.serviceAreas.forEachIndexed { index, area ->
            val profile = feederProfile(area, index)
            add(
                DemoLaneSeed(
                    code = feederOutCode(area.hubCode, area.gatewayCode),
                    fromHubCode = area.hubCode,
                    toHubCode = area.gatewayCode,
                    transportMode = TransportMode.ROAD,
                    estimatedTransitMinutes = profile.transitMinutes,
                    distanceKm = profile.distanceKm,
                    maxShipmentWeightKg = BigDecimal("1500.000"),
                    schedules = feederSchedules(profile.capacityKg, outbound = true),
                ),
            )
            add(
                DemoLaneSeed(
                    code = feederInCode(area.gatewayCode, area.hubCode),
                    fromHubCode = area.gatewayCode,
                    toHubCode = area.hubCode,
                    transportMode = TransportMode.ROAD,
                    estimatedTransitMinutes = profile.transitMinutes,
                    distanceKm = profile.distanceKm,
                    maxShipmentWeightKg = BigDecimal("1500.000"),
                    schedules = feederSchedules(profile.capacityKg, outbound = false),
                ),
            )
        }

        addAll(trunkLanes())
    }.also(::validateLanes)

    fun routeTemplates(): List<DemoRouteTemplateSeed> = buildList {
        metros.forEach { origin ->
            metros.filter { it != origin }.forEach { destination ->
                val via = metros.single { it != origin && it != destination }
                val baseCode = "${origin.prefix}-${destination.prefix}"
                val displayName = "${origin.name} → ${destination.name}"

                add(
                    DemoRouteTemplateSeed(
                        code = "$baseCode-EXPRESS",
                        name = "$displayName · Express",
                        description = "Ưu tiên đường hàng không trực tiếp giữa hai gateway.",
                        originHubCode = origin.majorHubCode,
                        destinationHubCode = destination.majorHubCode,
                        priority = 10,
                        laneCodes = listOf(
                            feederOutCode(origin.majorHubCode, origin.gatewayCode),
                            airCode(origin.prefix, destination.prefix),
                            feederInCode(destination.gatewayCode, destination.majorHubCode),
                        ),
                    ),
                )
                add(
                    DemoRouteTemplateSeed(
                        code = "$baseCode-STANDARD",
                        name = "$displayName · Standard",
                        description = "Đường bộ trực tiếp giữa hai gateway, tải lớn và chi phí thấp hơn.",
                        originHubCode = origin.majorHubCode,
                        destinationHubCode = destination.majorHubCode,
                        priority = 100,
                        laneCodes = listOf(
                            feederOutCode(origin.majorHubCode, origin.gatewayCode),
                            roadCode(origin.prefix, destination.prefix),
                            feederInCode(destination.gatewayCode, destination.majorHubCode),
                        ),
                    ),
                )
                add(
                    DemoRouteTemplateSeed(
                        code = "$baseCode-ECONOMY",
                        name = "$displayName · Economy",
                        description = "Tuyến dự phòng đi qua ${via.name}, dùng để thử multi-hop và capacity fallback.",
                        originHubCode = origin.majorHubCode,
                        destinationHubCode = destination.majorHubCode,
                        priority = 200,
                        laneCodes = listOf(
                            feederOutCode(origin.majorHubCode, origin.gatewayCode),
                            roadCode(origin.prefix, via.prefix),
                            roadCode(via.prefix, destination.prefix),
                            feederInCode(destination.gatewayCode, destination.majorHubCode),
                        ),
                    ),
                )
            }
        }
    }.also { validateTemplates(it, lanes()) }

    fun feederOutCode(localHubCode: String, gatewayCode: String): String =
        "$localHubCode-TO-$gatewayCode"

    fun feederInCode(gatewayCode: String, localHubCode: String): String =
        "$gatewayCode-TO-$localHubCode"

    fun airCode(fromPrefix: String, toPrefix: String): String = "$fromPrefix-$toPrefix-AIR"
    fun roadCode(fromPrefix: String, toPrefix: String): String = "$fromPrefix-$toPrefix-ROAD"

    private data class FeederProfile(
        val transitMinutes: Int,
        val distanceKm: BigDecimal,
        val capacityKg: BigDecimal,
    )

    private fun feederProfile(area: DemoServiceAreaSeed, index: Int): FeederProfile {
        val remote = area.code.contains("HIGHLANDS") ||
            area.code.contains("SONTAY") ||
            area.code.contains("SOUTH-RURAL") ||
            area.code.contains("CUCHI") ||
            area.code.contains("CANGIO") ||
            area.code.contains("CONDAO") ||
            area.code.contains("XUYENMOC") ||
            area.code.contains("DAUTIENG")

        val central = area.code in setOf("HN-CENTRAL", "DN-URBAN", "HCM-CENTRAL")
        val transit = when {
            central -> 35
            remote -> 150 + (index % 3) * 30
            else -> 60 + (index % 4) * 20
        }
        val distance = when {
            central -> BigDecimal("18.00")
            remote -> BigDecimal.valueOf((95 + (index % 5) * 35).toLong())
            else -> BigDecimal.valueOf((30 + (index % 6) * 18).toLong())
        }
        val capacity = when {
            central -> BigDecimal("12000.000")
            remote -> BigDecimal("4500.000")
            else -> BigDecimal("7000.000")
        }
        return FeederProfile(transit, distance, capacity)
    }

    private fun feederSchedules(baseCapacity: BigDecimal, outbound: Boolean): List<DemoScheduleSeed> {
        val times = if (outbound) {
            listOf(LocalTime.of(5, 30), LocalTime.of(13, 30), LocalTime.of(21, 30))
        } else {
            listOf(LocalTime.of(7, 30), LocalTime.of(15, 30), LocalTime.of(23, 30))
        }
        val factors = listOf(BigDecimal("1.00"), BigDecimal("0.85"), BigDecimal("0.70"))
        return times.zip(factors).map { (time, factor) ->
            DemoScheduleSeed(time, everyDay, baseCapacity.multiply(factor).setScale(3))
        }
    }

    private fun trunkLanes(): List<DemoLaneSeed> {
        data class PairProfile(
            val a: String,
            val b: String,
            val airMinutes: Int,
            val airDistance: String,
            val roadMinutes: Int,
            val roadDistance: String,
            val airCapacity: String,
            val roadCapacity: String,
        )

        val pairs = listOf(
            PairProfile("HN", "DN", 95, "620.00", 780, "770.00", "3200.000", "18000.000"),
            PairProfile("DN", "HCM", 95, "610.00", 960, "960.00", "3500.000", "20000.000"),
            PairProfile("HN", "HCM", 135, "1170.00", 1800, "1720.00", "2200.000", "22000.000"),
        )
        val gatewayByPrefix = metros.associate { it.prefix to it.gatewayCode }

        return buildList {
            pairs.forEach { pair ->
                listOf(pair.a to pair.b, pair.b to pair.a).forEachIndexed { directionIndex, (from, to) ->
                    val airTimes = when ("$from-$to") {
                        "HN-DN" -> listOf("08:00", "14:30", "21:00")
                        "DN-HN" -> listOf("07:30", "14:00", "20:30")
                        "DN-HCM" -> listOf("09:30", "16:00", "22:30")
                        "HCM-DN" -> listOf("08:30", "15:00", "21:30")
                        "HN-HCM" -> listOf("09:00", "16:00", "22:30")
                        else -> listOf("08:00", "15:30", "22:00") // HCM-HN
                    }
                    val airBase = BigDecimal(pair.airCapacity)
                    add(
                        DemoLaneSeed(
                            code = airCode(from, to),
                            fromHubCode = requireNotNull(gatewayByPrefix[from]),
                            toHubCode = requireNotNull(gatewayByPrefix[to]),
                            transportMode = TransportMode.AIR,
                            estimatedTransitMinutes = pair.airMinutes,
                            distanceKm = BigDecimal(pair.airDistance),
                            maxShipmentWeightKg = BigDecimal("500.000"),
                            schedules = airTimes.mapIndexed { index, value ->
                                DemoScheduleSeed(
                                    departureTime = LocalTime.parse(value),
                                    daysOfWeek = everyDay,
                                    capacityKg = airBase
                                        .multiply(listOf(BigDecimal("1.00"), BigDecimal("0.85"), BigDecimal("0.70"))[index])
                                        .setScale(3),
                                )
                            },
                        ),
                    )

                    val roadBase = BigDecimal(pair.roadCapacity)
                    add(
                        DemoLaneSeed(
                            code = roadCode(from, to),
                            fromHubCode = requireNotNull(gatewayByPrefix[from]),
                            toHubCode = requireNotNull(gatewayByPrefix[to]),
                            transportMode = TransportMode.ROAD,
                            estimatedTransitMinutes = pair.roadMinutes,
                            distanceKm = BigDecimal(pair.roadDistance),
                            maxShipmentWeightKg = BigDecimal("2500.000"),
                            schedules = listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)).mapIndexed { index, time ->
                                DemoScheduleSeed(
                                    departureTime = time.plusMinutes((directionIndex * 30).toLong()),
                                    daysOfWeek = everyDay,
                                    capacityKg = roadBase
                                        .multiply(if (index == 0) BigDecimal.ONE else BigDecimal("0.80"))
                                        .setScale(3),
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun validateLanes(lanes: List<DemoLaneSeed>) {
        require(lanes.size == 86) { "Demo transport must contain 86 lanes, found ${lanes.size}" }
        require(lanes.map { it.code }.distinct().size == lanes.size) { "Demo transport contains duplicate lane codes" }
        require(lanes.all { it.fromHubCode != it.toHubCode }) { "Demo transport contains a self-loop lane" }
        require(lanes.all { it.code.length <= 50 }) { "Demo transport contains a lane code longer than 50 characters" }
        require(lanes.sumOf { it.schedules.size } == 252) {
            "Demo transport must contain 252 schedules, found ${lanes.sumOf { it.schedules.size }}"
        }
        require(lanes.all { lane -> lane.schedules.all { it.capacityKg >= lane.maxShipmentWeightKg } }) {
            "Every demo schedule capacity must be at least the lane max shipment weight"
        }
    }

    private fun validateTemplates(templates: List<DemoRouteTemplateSeed>, lanes: List<DemoLaneSeed>) {
        require(templates.size == 18) { "Demo transport must contain 18 route templates" }
        require(templates.map { it.code }.distinct().size == templates.size) { "Duplicate demo route template code" }
        val laneByCode = lanes.associateBy { it.code }

        templates.forEach { template ->
            require(template.laneCodes.isNotEmpty()) { "Template ${template.code} has no lanes" }
            require(template.laneCodes.distinct().size == template.laneCodes.size) {
                "Template ${template.code} repeats a lane"
            }
            val path = template.laneCodes.map { code ->
                requireNotNull(laneByCode[code]) { "Template ${template.code} references unknown lane $code" }
            }
            require(path.first().fromHubCode == template.originHubCode) {
                "Template ${template.code} does not start at ${template.originHubCode}"
            }
            require(path.last().toHubCode == template.destinationHubCode) {
                "Template ${template.code} does not end at ${template.destinationHubCode}"
            }
            path.zipWithNext().forEach { (left, right) ->
                require(left.toHubCode == right.fromHubCode) {
                    "Template ${template.code} contains a disconnected path"
                }
            }
            val visited = mutableSetOf(path.first().fromHubCode)
            path.forEach { lane ->
                require(visited.add(lane.toHubCode)) { "Template ${template.code} contains a hub cycle" }
            }
        }
    }
}
