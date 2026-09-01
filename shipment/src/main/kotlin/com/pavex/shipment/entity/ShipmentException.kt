package com.pavex.shipment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class ShipmentExceptionRequestKind { RETURN, FORWARD, CANCELLATION }
enum class ShipmentExceptionRequestStatus { PENDING_APPROVAL, APPROVED, REJECTED, PROCESSING, COMPLETED }

@Entity
@Table(name = "shipment_exception_requests", schema = "shipments")
class ShipmentExceptionRequest(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var kind: ShipmentExceptionRequestKind = ShipmentExceptionRequestKind.RETURN,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ShipmentExceptionRequestStatus = ShipmentExceptionRequestStatus.PENDING_APPROVAL,
    @Column(nullable = false, length = 1000)
    var reason: String = "",
    @Column(name = "requested_by_user_id", nullable = false)
    var requestedByUserId: UUID = UUID.randomUUID(),
    @Column(name = "requested_at", nullable = false)
    var requestedAt: Instant = Instant.now(),
    @Column(name = "reviewed_by_user_id")
    var reviewedByUserId: UUID? = null,
    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,
    @Column(name = "review_note", length = 500)
    var reviewNote: String? = null,
    @Column(name = "routing_required", nullable = false)
    var routingRequired: Boolean = false,
    @Column(name = "forward_contact_name", length = 120)
    var forwardContactName: String? = null,
    @Column(name = "forward_contact_phone", length = 20)
    var forwardContactPhone: String? = null,
    @Column(name = "forward_address_line", length = 500)
    var forwardAddressLine: String? = null,
    @Column(name = "forward_province_code", length = 20)
    var forwardProvinceCode: String? = null,
    @Column(name = "forward_ward_code", length = 20)
    var forwardWardCode: String? = null,
    @Column(name = "completed_by_user_id")
    var completedByUserId: UUID? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Version
    var version: Long = 0,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun review(approve: Boolean, actorUserId: UUID, note: String?, now: Instant) {
        check(status == ShipmentExceptionRequestStatus.PENDING_APPROVAL) { "Only pending exception requests can be reviewed." }
        reviewedByUserId = actorUserId
        reviewedAt = now
        reviewNote = normalizeOptional(note, 500)
        status = if (approve) ShipmentExceptionRequestStatus.APPROVED else ShipmentExceptionRequestStatus.REJECTED
        routingRequired = approve && (kind == ShipmentExceptionRequestKind.RETURN || kind == ShipmentExceptionRequestKind.FORWARD)
        updatedAt = now
    }

    fun markProcessing(now: Instant) {
        check(status == ShipmentExceptionRequestStatus.APPROVED) { "Only approved exception requests can start processing." }
        status = ShipmentExceptionRequestStatus.PROCESSING
        updatedAt = now
    }

    fun complete(actorUserId: UUID, now: Instant) {
        check(status == ShipmentExceptionRequestStatus.APPROVED || status == ShipmentExceptionRequestStatus.PROCESSING) {
            "Only approved or processing exception requests can be completed."
        }
        status = ShipmentExceptionRequestStatus.COMPLETED
        completedByUserId = actorUserId
        completedAt = now
        updatedAt = now
    }

    companion object {
        fun create(
            shipmentId: UUID,
            kind: ShipmentExceptionRequestKind,
            reason: String,
            actorUserId: UUID,
            now: Instant,
            forwardRecipient: ForwardRecipientSnapshot? = null,
        ): ShipmentExceptionRequest {
            val normalizedReason = requireText(reason, 1000, "reason")
            if (kind == ShipmentExceptionRequestKind.FORWARD) requireNotNull(forwardRecipient) { "Forward recipient is required." }
            return ShipmentExceptionRequest(
                shipmentId = shipmentId,
                kind = kind,
                reason = normalizedReason,
                requestedByUserId = actorUserId,
                requestedAt = now,
                updatedAt = now,
                forwardContactName = forwardRecipient?.contactName,
                forwardContactPhone = forwardRecipient?.contactPhone,
                forwardAddressLine = forwardRecipient?.addressLine,
                forwardProvinceCode = forwardRecipient?.provinceCode,
                forwardWardCode = forwardRecipient?.wardCode,
            )
        }

        private fun requireText(value: String?, maximum: Int, field: String): String {
            val normalized = value?.trim().orEmpty()
            require(normalized.isNotEmpty() && normalized.length <= maximum) { "$field is required and must not exceed $maximum characters." }
            return normalized
        }

        private fun normalizeOptional(value: String?, maximum: Int): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= maximum) { "Text must not exceed $maximum characters." }
        }
    }
}

data class ForwardRecipientSnapshot(
    val contactName: String,
    val contactPhone: String,
    val addressLine: String,
    val provinceCode: String,
    val wardCode: String,
)

@Entity
@Table(name = "shipment_weight_adjustments", schema = "shipments")
class ShipmentWeightAdjustment(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "parcel_id", nullable = false)
    var parcelId: UUID = UUID.randomUUID(),
    @Column(name = "old_actual_weight_kg", nullable = false, precision = 12, scale = 3)
    var oldActualWeightKg: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    @Column(name = "new_actual_weight_kg", nullable = false, precision = 12, scale = 3)
    var newActualWeightKg: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    @Column(name = "old_chargeable_weight_kg", nullable = false, precision = 12, scale = 3)
    var oldChargeableWeightKg: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    @Column(name = "new_chargeable_weight_kg", nullable = false, precision = 12, scale = 3)
    var newChargeableWeightKg: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    @Column(nullable = false, length = 1000)
    var reason: String = "",
    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: UUID = UUID.randomUUID(),
    @Column(name = "hub_id")
    var hubId: UUID? = null,
    @Column(name = "adjusted_at", nullable = false)
    var adjustedAt: Instant = Instant.now(),
    @Column(name = "review_completed", nullable = false)
    var reviewCompleted: Boolean = false,
    @Column(name = "reviewed_by_user_id")
    var reviewedByUserId: UUID? = null,
    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,
    @Column(name = "review_note", length = 500)
    var reviewNote: String? = null,
    @Version
    var version: Long = 0,
) {
    fun completeReview(actorUserId: UUID, note: String, now: Instant) {
        check(!reviewCompleted) { "Weight review is already completed." }
        val normalized = note.trim()
        require(normalized.isNotEmpty() && normalized.length <= 500) { "Review note is required and must not exceed 500 characters." }
        reviewCompleted = true
        reviewedByUserId = actorUserId
        reviewedAt = now
        reviewNote = normalized
    }
}
