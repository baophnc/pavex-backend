package com.pavex.network.service

import com.pavex.network.entity.LaneSchedule
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class DepartureKey(
    val scheduleId: UUID,
    val serviceDate: LocalDate,
)

class DepartureCapacitySnapshot(
    private val allocatedByDeparture: Map<DepartureKey, BigDecimal>,
    private val excludedDepartures: Set<DepartureKey> = emptySet(),
) {
    fun allocated(schedule: LaneSchedule, serviceDate: LocalDate): BigDecimal =
        allocatedByDeparture[DepartureKey(schedule.id, serviceDate)] ?: BigDecimal.ZERO

    fun remaining(schedule: LaneSchedule, serviceDate: LocalDate): BigDecimal =
        schedule.capacityKg.subtract(allocated(schedule, serviceDate)).max(BigDecimal.ZERO)

    fun canCarry(
        schedule: LaneSchedule,
        serviceDate: LocalDate,
        shipmentWeightKg: BigDecimal,
    ): Boolean {
        val key = DepartureKey(schedule.id, serviceDate)
        return key !in excludedDepartures && remaining(schedule, serviceDate) >= shipmentWeightKg
    }

    companion object {
        val EMPTY = DepartureCapacitySnapshot(emptyMap())
    }
}
