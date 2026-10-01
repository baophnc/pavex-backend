package com.pavex.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/** HELD by the courier → SUBMITTED to a hub in a remittance → APPROVED by finance. */
enum class CashCollectionStatus { HELD, SUBMITTED, APPROVED }

/** Where the cash was taken: from a recipient on delivery, or from a walk-in sender at a counter. */
enum class CashSource { DELIVERY, COUNTER }

/** Who the COD in the cash belongs to: a merchant (settled), or a walk-in sender (paid at the counter). */
enum class CodPayee { MERCHANT, SENDER }

/**
 * Money a courier collected from a recipient on delivery: the merchant's COD plus the shipping
 * fee when the recipient paid it. It stays in the courier's custody until a hub takes it over.
 * Counter cash (a walk-in sender's fee) starts at the hub, with no courier holding it.
 */
@Entity
@Table(name = "cash_collections", schema = "billing")
class CashCollection(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "tracking_code", nullable = false, length = 40)
    var trackingCode: String = "",
    @Column(name = "merchant_id")
    var merchantId: UUID? = null,
    @Column(name = "delivery_attempt_id")
    var deliveryAttemptId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var source: CashSource = CashSource.DELIVERY,
    @Enumerated(EnumType.STRING)
    @Column(name = "cod_payee", nullable = false, length = 20)
    var codPayee: CodPayee = CodPayee.MERCHANT,
    @Column(nullable = false)
    var amount: Long = 0,
    @Column(name = "cod_amount", nullable = false)
    var codAmount: Long = 0,
    @Column(name = "fee_amount", nullable = false)
    var feeAmount: Long = 0,
    @Column(nullable = false, length = 3)
    var currency: String = "VND",
    @Column(nullable = false, length = 100)
    var reference: String = "",
    @Column(name = "collected_by_user_id", nullable = false)
    var collectedByUserId: UUID = UUID.randomUUID(),
    @Column(name = "origin_hub_id", nullable = false)
    var originHubId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CashCollectionStatus = CashCollectionStatus.HELD,
    @Column(name = "current_custodian_user_id")
    var currentCustodianUserId: UUID? = null,
    @Column(name = "current_hub_id")
    var currentHubId: UUID? = null,
    @Column(name = "current_remittance_id")
    var currentRemittanceId: UUID? = null,
    @Column(name = "collected_at", nullable = false)
    var collectedAt: Instant = Instant.EPOCH,
    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
    @Column(name = "approved_by_user_id")
    var approvedByUserId: UUID? = null,
    @Column(name = "last_rejected_at")
    var lastRejectedAt: Instant? = null,
    @Column(name = "last_rejected_by_user_id")
    var lastRejectedByUserId: UUID? = null,
    @Column(name = "last_rejection_reason", length = 500)
    var lastRejectionReason: String? = null,
    @Column(name = "statement_id")
    var statementId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    fun isHeldBy(courierUserId: UUID): Boolean =
        status == CashCollectionStatus.HELD && currentCustodianUserId == courierUserId && collectedByUserId == courierUserId

    fun submitToHub(remittanceId: UUID, hubId: UUID, submittedAt: Instant) {
        check(isHeldBy(collectedByUserId)) { "Cash collection is not held by the collecting courier." }
        currentCustodianUserId = null
        currentHubId = hubId
        currentRemittanceId = remittanceId
        status = CashCollectionStatus.SUBMITTED
        this.submittedAt = submittedAt
        updatedAt = submittedAt
    }

    /** The hub keeps the cash; finance did not accept it yet. */
    fun rejectToHub(hubId: UUID, actorUserId: UUID, reason: String, rejectedAt: Instant) {
        check(status == CashCollectionStatus.SUBMITTED) { "Only submitted cash can be rejected." }
        status = CashCollectionStatus.HELD
        currentCustodianUserId = null
        currentHubId = hubId
        currentRemittanceId = null
        lastRejectedAt = rejectedAt
        lastRejectedByUserId = actorUserId
        lastRejectionReason = BillingText.required(reason, 500, "reason")
        updatedAt = rejectedAt
    }

    fun resubmitFromHub(submittedAt: Instant) {
        check(status == CashCollectionStatus.HELD && currentHubId != null) { "Only hub-held cash can be resubmitted." }
        status = CashCollectionStatus.SUBMITTED
        currentRemittanceId = null
        this.submittedAt = submittedAt
        updatedAt = submittedAt
    }

    fun approve(actorUserId: UUID, approvedAt: Instant) {
        check(status == CashCollectionStatus.SUBMITTED) { "Only submitted cash can be approved." }
        status = CashCollectionStatus.APPROVED
        approvedByUserId = actorUserId
        this.approvedAt = approvedAt
        currentCustodianUserId = null
        updatedAt = approvedAt
    }

    fun includeInStatement(statementId: UUID, at: Instant) {
        check(status == CashCollectionStatus.APPROVED) { "Only approved cash can be settled." }
        check(this.statementId == null) { "Cash collection is already settled." }
        this.statementId = statementId
        updatedAt = at
    }

    companion object {
        fun fromDelivery(
            shipmentId: UUID,
            trackingCode: String,
            merchantId: UUID?,
            deliveryAttemptId: UUID,
            courierUserId: UUID,
            hubId: UUID,
            codAmount: Long,
            feeAmount: Long,
            collectedAt: Instant,
        ): CashCollection {
            require(codAmount >= 0 && feeAmount >= 0) { "Collected amounts cannot be negative." }
            val amount = Math.addExact(codAmount, feeAmount)
            require(amount > 0) { "Cash collection amount must be positive." }
            val code = BillingText.required(trackingCode, 40, "trackingCode").uppercase()
            return CashCollection(
                shipmentId = shipmentId,
                trackingCode = code,
                merchantId = merchantId,
                deliveryAttemptId = deliveryAttemptId,
                source = CashSource.DELIVERY,
                // A shipment without a merchant was booked at a counter: its COD goes back to the sender.
                codPayee = if (merchantId == null) CodPayee.SENDER else CodPayee.MERCHANT,
                amount = amount,
                codAmount = codAmount,
                feeAmount = feeAmount,
                reference = "CASH-$code",
                collectedByUserId = courierUserId,
                originHubId = hubId,
                status = CashCollectionStatus.HELD,
                currentCustodianUserId = courierUserId,
                collectedAt = collectedAt,
                createdAt = collectedAt,
                updatedAt = collectedAt,
            )
        }

        /**
         * Cash a walk-in sender paid at a hub counter: the shipping fee at booking, or the fee of a
         * returned shipment. The hub holds it until it hands it to finance like any other cash.
         */
        fun fromCounter(
            shipmentId: UUID,
            trackingCode: String,
            clerkUserId: UUID,
            hubId: UUID,
            feeAmount: Long,
            reference: String,
            paidAt: Instant,
        ): CashCollection {
            require(feeAmount > 0) { "Counter cash must be positive." }
            val code = BillingText.required(trackingCode, 40, "trackingCode").uppercase()
            return CashCollection(
                shipmentId = shipmentId,
                trackingCode = code,
                source = CashSource.COUNTER,
                codPayee = CodPayee.SENDER,
                amount = feeAmount,
                codAmount = 0,
                feeAmount = feeAmount,
                reference = BillingText.required(reference, 100, "reference"),
                collectedByUserId = clerkUserId,
                originHubId = hubId,
                status = CashCollectionStatus.HELD,
                currentHubId = hubId,
                collectedAt = paidAt,
                createdAt = paidAt,
                updatedAt = paidAt,
            )
        }
    }
}
