package com.pavex.network.service

import com.pavex.network.entity.LaneCapacityReservationStatus
import com.pavex.network.entity.LaneSchedule
import com.pavex.network.repository.LaneCapacityReservationRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant

@Component
class DepartureCapacitySnapshotFactory(
    private val reservations: LaneCapacityReservationRepository,
) {
    fun build(
        schedules: List<LaneSchedule>,
        readyAt: Instant,
        searchDays: Int,
        excludedDepartures: Set<DepartureKey> = emptySet(),
    ): DepartureCapacitySnapshot {
        if (schedules.isEmpty()) return DepartureCapacitySnapshot(emptyMap(), excludedDepartures)
        val fromDate = readyAt.atZone(LaneScheduleCalculator.VIETNAM_ZONE).toLocalDate()
        val toDate = fromDate.plusDays(searchDays.toLong())
        val allocated = reservations.findAllByScheduleIdInAndServiceDateBetweenAndStatusIn(
            schedules.map { it.id },
            fromDate,
            toDate,
            ALLOCATED_STATUSES,
        ).groupBy { DepartureKey(it.scheduleId, it.serviceDate) }
            .mapValues { (_, items) -> items.fold(BigDecimal.ZERO) { total, item -> total.add(item.reservedWeightKg) } }
        return DepartureCapacitySnapshot(allocated, excludedDepartures)
    }

    companion object {
        private val ALLOCATED_STATUSES = listOf(
            LaneCapacityReservationStatus.RESERVED,
            LaneCapacityReservationStatus.CONSUMED,
        )
    }
}
