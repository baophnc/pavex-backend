package com.pavex.network.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class LaneCapacityReservationStatus { RESERVED, CONSUMED, RELEASED }
enum class CapacityRouteSource { ROUTE_TEMPLATE, GRAPH }

@Entity
@Table(name = "lane_capacity_reservations", schema = "network")
class LaneCapacityReservation(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "batch_id", nullable = false)
    var batchId: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "route_revision", nullable = false)
    var routeRevision: Int = 1,
    @Column(name = "lane_id", nullable = false)
    var laneId: UUID = UUID.randomUUID(),
    @Column(name = "schedule_id", nullable = false)
    var scheduleId: UUID = UUID.randomUUID(),
    @Column(name = "leg_sequence", nullable = false)
    var legSequence: Int = 1,
    @Column(name = "service_date", nullable = false)
    var serviceDate: LocalDate = LocalDate.now(),
    @Column(name = "departure_at", nullable = false)
    var departureAt: Instant = Instant.now(),
    @Column(name = "arrival_at", nullable = false)
    var arrivalAt: Instant = Instant.now(),
    @Column(name = "reserved_weight_kg", nullable = false, precision = 14, scale = 3)
    var reservedWeightKg: BigDecimal = BigDecimal.ONE,
    @Column(name = "departure_capacity_kg", nullable = false, precision = 14, scale = 3)
    var departureCapacityKg: BigDecimal = BigDecimal.ONE,
    @Enumerated(EnumType.STRING)
    @Column(name = "route_source", nullable = false, length = 30)
    var routeSource: CapacityRouteSource = CapacityRouteSource.GRAPH,
    @Column(name = "route_template_id")
    var routeTemplateId: UUID? = null,
    @Column(name = "route_template_code", length = 80)
    var routeTemplateCode: String? = null,
    @Column(name = "route_template_revision")
    var routeTemplateRevision: Int? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: LaneCapacityReservationStatus = LaneCapacityReservationStatus.RESERVED,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Column(name = "confirmation_deadline_at")
    var confirmationDeadlineAt: Instant? = null,
    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,
    @Version
    var version: Long = 0,
) {
    fun consume() {
        if (status == LaneCapacityReservationStatus.CONSUMED) return
        check(status != LaneCapacityReservationStatus.RELEASED) {
            "Released capacity cannot be consumed."
        }
        status = LaneCapacityReservationStatus.CONSUMED
        updatedAt = Instant.now()
    }

    /** The owning shipment exists, so this reservation must never be expired. */
    fun confirm(now: Instant) {
        if (confirmedAt != null) return
        confirmedAt = now
        updatedAt = now
    }

    fun isExpiredUnconfirmed(now: Instant): Boolean {
        val deadline = confirmationDeadlineAt ?: return false
        return status == LaneCapacityReservationStatus.RESERVED && confirmedAt == null && !deadline.isAfter(now)
    }

    fun release() {
        if (status == LaneCapacityReservationStatus.RELEASED) return
        check(status != LaneCapacityReservationStatus.CONSUMED) {
            "Consumed capacity cannot be released."
        }
        status = LaneCapacityReservationStatus.RELEASED
        updatedAt = Instant.now()
    }
}
