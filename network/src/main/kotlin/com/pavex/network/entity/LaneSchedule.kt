package com.pavex.network.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class LaneScheduleStatus { INACTIVE, ACTIVE }

@Entity
@Table(name = "lane_schedules", schema = "network")
class LaneSchedule(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "lane_id", nullable = false)
    var laneId: UUID = UUID.randomUUID(),
    @Column(name = "departure_time", nullable = false)
    var departureTime: LocalTime = LocalTime.MIDNIGHT,
    @Column(nullable = false)
    var monday: Boolean = false,
    @Column(nullable = false)
    var tuesday: Boolean = false,
    @Column(nullable = false)
    var wednesday: Boolean = false,
    @Column(nullable = false)
    var thursday: Boolean = false,
    @Column(nullable = false)
    var friday: Boolean = false,
    @Column(nullable = false)
    var saturday: Boolean = false,
    @Column(nullable = false)
    var sunday: Boolean = false,
    @Column(name = "effective_from", nullable = false)
    var effectiveFrom: LocalDate = LocalDate.now(),
    @Column(name = "effective_to")
    var effectiveTo: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: LaneScheduleStatus = LaneScheduleStatus.ACTIVE,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun update(
        departureTime: LocalTime,
        daysOfWeek: Set<DayOfWeek>,
        effectiveFrom: LocalDate,
        effectiveTo: LocalDate?,
    ) {
        this.departureTime = departureTime
        setDays(daysOfWeek)
        this.effectiveFrom = effectiveFrom
        this.effectiveTo = effectiveTo
        updatedAt = Instant.now()
    }

    fun setActive(active: Boolean) {
        status = if (active) LaneScheduleStatus.ACTIVE else LaneScheduleStatus.INACTIVE
        updatedAt = Instant.now()
    }

    fun setDays(daysOfWeek: Set<DayOfWeek>) {
        monday = DayOfWeek.MONDAY in daysOfWeek
        tuesday = DayOfWeek.TUESDAY in daysOfWeek
        wednesday = DayOfWeek.WEDNESDAY in daysOfWeek
        thursday = DayOfWeek.THURSDAY in daysOfWeek
        friday = DayOfWeek.FRIDAY in daysOfWeek
        saturday = DayOfWeek.SATURDAY in daysOfWeek
        sunday = DayOfWeek.SUNDAY in daysOfWeek
    }

    fun daysOfWeek(): Set<DayOfWeek> = linkedSetOf<DayOfWeek>().apply {
        if (monday) add(DayOfWeek.MONDAY)
        if (tuesday) add(DayOfWeek.TUESDAY)
        if (wednesday) add(DayOfWeek.WEDNESDAY)
        if (thursday) add(DayOfWeek.THURSDAY)
        if (friday) add(DayOfWeek.FRIDAY)
        if (saturday) add(DayOfWeek.SATURDAY)
        if (sunday) add(DayOfWeek.SUNDAY)
    }

    fun operatesOn(date: LocalDate): Boolean {
        if (date.isBefore(effectiveFrom) || effectiveTo?.let(date::isAfter) == true) return false
        return date.dayOfWeek in daysOfWeek()
    }

    companion object {
        fun create(
            laneId: UUID,
            departureTime: LocalTime,
            daysOfWeek: Set<DayOfWeek>,
            effectiveFrom: LocalDate,
            effectiveTo: LocalDate?,
            active: Boolean,
        ): LaneSchedule = LaneSchedule(
            laneId = laneId,
            departureTime = departureTime,
            effectiveFrom = effectiveFrom,
            effectiveTo = effectiveTo,
            status = if (active) LaneScheduleStatus.ACTIVE else LaneScheduleStatus.INACTIVE,
        ).also { it.setDays(daysOfWeek) }
    }
}
