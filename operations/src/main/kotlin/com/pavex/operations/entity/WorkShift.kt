package com.pavex.operations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class WorkShiftStatus { SCHEDULED, CANCELLED }

@Entity
@Table(name = "work_shifts", schema = "operations")
class WorkShift(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "hub_id", nullable = false)
    var hubId: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 24)
    var code: String = "",
    @Column(nullable = false, length = 100)
    var name: String = "",
    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant = Instant.now(),
    @Column(name = "ends_at", nullable = false)
    var endsAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: WorkShiftStatus = WorkShiftStatus.SCHEDULED,
    @Column(name = "created_by_user_id", nullable = false)
    var createdByUserId: UUID = UUID.randomUUID(),
    @Column(name = "cancelled_by_user_id")
    var cancelledByUserId: UUID? = null,
    @Column(name = "cancel_reason", length = 500)
    var cancelReason: String? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun updateSchedule(name: String, startsAt: Instant, endsAt: Instant, now: Instant): Boolean {
        ensureEditable(now)
        validateName(name)
        validateWindow(startsAt, endsAt)
        require(startsAt > now) { "A shift must start in the future." }
        val normalized = name.trim()
        if (this.name == normalized && this.startsAt == startsAt && this.endsAt == endsAt) return false
        this.name = normalized
        this.startsAt = startsAt
        this.endsAt = endsAt
        updatedAt = now
        return true
    }

    fun cancel(actorUserId: UUID, reason: String, now: Instant): Boolean {
        require(actorUserId != ZERO_UUID) { "Actor is required." }
        val normalizedReason = reason.trim()
        require(normalizedReason.isNotEmpty()) { "A cancellation reason is required." }
        require(normalizedReason.length <= 500) { "Cancellation reason must not exceed 500 characters." }
        if (status == WorkShiftStatus.CANCELLED) return false
        status = WorkShiftStatus.CANCELLED
        cancelledByUserId = actorUserId
        cancelReason = normalizedReason
        cancelledAt = now
        updatedAt = now
        return true
    }

    private fun ensureEditable(now: Instant) {
        check(status != WorkShiftStatus.CANCELLED) { "A cancelled shift cannot be edited." }
        check(startsAt > now) { "A shift cannot be edited after it has started." }
    }

    companion object {
        private val ZERO_UUID: UUID = UUID(0, 0)

        fun create(
            hubId: UUID,
            name: String,
            startsAt: Instant,
            endsAt: Instant,
            createdByUserId: UUID,
            now: Instant,
        ): WorkShift {
            require(hubId != ZERO_UUID && createdByUserId != ZERO_UUID) { "Hub and creator are required." }
            validateName(name)
            validateWindow(startsAt, endsAt)
            require(startsAt > now) { "A shift must start in the future." }
            val id = UUID.randomUUID()
            val compact = id.toString().replace("-", "").uppercase()
            return WorkShift(
                id = id,
                hubId = hubId,
                code = "PAVEX-SH-${compact.take(11)}",
                name = name.trim(),
                startsAt = startsAt,
                endsAt = endsAt,
                status = WorkShiftStatus.SCHEDULED,
                createdByUserId = createdByUserId,
                createdAt = now,
                updatedAt = now,
            )
        }

        private fun validateName(name: String) {
            val normalized = name.trim()
            require(normalized.isNotEmpty() && normalized.length <= 100) {
                "Shift name is required and must not exceed 100 characters."
            }
        }

        private fun validateWindow(startsAt: Instant, endsAt: Instant) {
            require(endsAt > startsAt) { "Shift end must be after shift start." }
            require(Duration.between(startsAt, endsAt) <= Duration.ofHours(24)) {
                "A shift cannot be longer than 24 hours."
            }
        }
    }
}
