package com.pavex.shipment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "delivery_attempts",
    schema = "shipments",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_delivery_attempt_parcel_key", columnNames = ["parcel_id", "idempotency_key"]),
        UniqueConstraint(name = "ux_delivery_attempt_parcel_number", columnNames = ["parcel_id", "attempt_number"]),
    ],
)
class DeliveryAttempt(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    var shipment: Shipment = Shipment(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false)
    var parcel: Parcel = Parcel(),
    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: UUID = UUID.randomUUID(),
    @Column(name = "attempt_number", nullable = false)
    var attemptNumber: Int = 1,
    @Column(name = "idempotency_key", nullable = false, length = 100)
    var idempotencyKey: String = "",
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    var requestFingerprint: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var outcome: DeliveryAttemptOutcome = DeliveryAttemptOutcome.FAILED,
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    var failureReason: DeliveryFailureReason? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "proof_method", length = 20)
    var proofMethod: DeliveryProofMethod? = null,
    @Column(name = "recipient_name", length = 200)
    var recipientName: String? = null,
    @Column(name = "evidence_reference", length = 500)
    var evidenceReference: String? = null,
    @Column(length = 500)
    var note: String? = null,
    @Column(precision = 9, scale = 6)
    var latitude: BigDecimal? = null,
    @Column(precision = 9, scale = 6)
    var longitude: BigDecimal? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    companion object {
        fun create(
            shipment: Shipment,
            parcel: Parcel,
            actorUserId: UUID,
            attemptNumber: Int,
            idempotencyKey: String,
            requestFingerprint: String,
            outcome: DeliveryAttemptOutcome,
            failureReason: DeliveryFailureReason?,
            proofMethod: DeliveryProofMethod?,
            recipientName: String?,
            evidenceReference: String?,
            note: String?,
            latitude: BigDecimal?,
            longitude: BigDecimal?,
            occurredAt: Instant,
            createdAt: Instant,
        ): DeliveryAttempt {
            require(actorUserId != UUID(0, 0) && attemptNumber > 0) { "Actor and attempt number are required." }
            val key = requireText(idempotencyKey, 100, "idempotencyKey")
            require(key.length >= 8) { "idempotencyKey must contain at least 8 characters." }
            val fingerprint = requestFingerprint.trim().uppercase()
            require(fingerprint.length == 64 && fingerprint.all { it in "0123456789ABCDEF" }) {
                "requestFingerprint must be a SHA-256 hexadecimal value."
            }
            validateLocation(latitude, longitude)
            val normalizedRecipient = optionalText(recipientName, 200)
            val normalizedEvidence = optionalText(evidenceReference, 500)
            val normalizedNote = optionalText(note, 500)

            if (outcome == DeliveryAttemptOutcome.DELIVERED) {
                require(failureReason == null) { "Successful delivery cannot include a failure reason." }
                require(proofMethod != null && normalizedRecipient != null && normalizedEvidence != null) {
                    "Successful delivery requires proof method, recipient and evidence reference."
                }
            } else {
                require(failureReason != null) { "Failed delivery requires a failure reason." }
                require(proofMethod == null && normalizedRecipient == null && normalizedEvidence == null) {
                    "Failed delivery cannot include successful-delivery proof fields."
                }
            }

            return DeliveryAttempt(
                shipment = shipment,
                parcel = parcel,
                actorUserId = actorUserId,
                attemptNumber = attemptNumber,
                idempotencyKey = key,
                requestFingerprint = fingerprint,
                outcome = outcome,
                failureReason = failureReason,
                proofMethod = proofMethod,
                recipientName = normalizedRecipient,
                evidenceReference = normalizedEvidence,
                note = normalizedNote,
                latitude = latitude,
                longitude = longitude,
                occurredAt = occurredAt,
                createdAt = createdAt,
            )
        }

        private fun validateLocation(latitude: BigDecimal?, longitude: BigDecimal?) {
            require((latitude == null) == (longitude == null)) { "Latitude and longitude must be supplied together." }
            if (latitude != null && longitude != null) {
                require(latitude >= BigDecimal("-90") && latitude <= BigDecimal("90")) { "Latitude is invalid." }
                require(longitude >= BigDecimal("-180") && longitude <= BigDecimal("180")) { "Longitude is invalid." }
            }
        }

        private fun requireText(value: String, maximum: Int, field: String): String =
            value.trim().also { require(it.isNotEmpty() && it.length <= maximum) { "$field is required and must not exceed $maximum characters." } }

        private fun optionalText(value: String?, maximum: Int): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= maximum) { "Text must not exceed $maximum characters." }
        }
    }
}

enum class DeliveryAttemptOutcome { DELIVERED, FAILED }

enum class DeliveryFailureReason {
    RECIPIENT_UNAVAILABLE,
    RECIPIENT_REFUSED,
    INVALID_ADDRESS,
    UNABLE_TO_CONTACT,
    PAYMENT_NOT_COLLECTED,
    PARCEL_DAMAGED,
    UNSAFE_LOCATION,
    OTHER,
}

enum class DeliveryProofMethod { OTP, SIGNATURE, PHOTO }
