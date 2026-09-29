package com.pavex.billing.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class CashRemittanceStatus { SUBMITTED, APPROVED, REJECTED }

/** A courier hands a batch of collected cash to a hub; the declared amount must match exactly. */
@Entity
@Table(name = "cash_remittances", schema = "billing")
class CashRemittance(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "remittance_number", nullable = false, length = 60)
    var remittanceNumber: String = "",
    @Column(name = "courier_user_id", nullable = false)
    var courierUserId: UUID = UUID.randomUUID(),
    @Column(name = "hub_id", nullable = false)
    var hubId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CashRemittanceStatus = CashRemittanceStatus.SUBMITTED,
    @Column(name = "expected_amount", nullable = false)
    var expectedAmount: Long = 0,
    @Column(name = "declared_amount", nullable = false)
    var declaredAmount: Long = 0,
    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.EPOCH,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
    @Column(name = "approved_by_user_id")
    var approvedByUserId: UUID? = null,
    @Column(name = "rejected_at")
    var rejectedAt: Instant? = null,
    @Column(name = "rejected_by_user_id")
    var rejectedByUserId: UUID? = null,
    @Column(name = "rejection_reason", length = 500)
    var rejectionReason: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    @OneToMany(mappedBy = "remittance", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    val items: MutableList<CashRemittanceItem> = mutableListOf()

    fun approve(actorUserId: UUID, approvedAt: Instant) {
        check(status == CashRemittanceStatus.SUBMITTED) { "Only submitted remittances can be approved." }
        status = CashRemittanceStatus.APPROVED
        this.approvedAt = approvedAt
        approvedByUserId = actorUserId
        updatedAt = approvedAt
    }

    fun reject(actorUserId: UUID, reason: String, rejectedAt: Instant) {
        check(status == CashRemittanceStatus.SUBMITTED) { "Only submitted remittances can be rejected." }
        status = CashRemittanceStatus.REJECTED
        this.rejectedAt = rejectedAt
        rejectedByUserId = actorUserId
        rejectionReason = BillingText.required(reason, 500, "reason")
        updatedAt = rejectedAt
    }

    companion object {
        const val MAX_ITEMS = 200

        fun create(
            remittanceNumber: String,
            courierUserId: UUID,
            hubId: UUID,
            collections: List<CashCollection>,
            declaredAmount: Long,
            submittedAt: Instant,
        ): CashRemittance {
            require(collections.size in 1..MAX_ITEMS) { "A remittance must contain between 1 and $MAX_ITEMS cash collections." }
            require(collections.map { it.id }.toSet().size == collections.size) { "Cash collections in a remittance must be unique." }
            val expected = collections.fold(0L) { total, collection -> Math.addExact(total, collection.amount) }
            require(declaredAmount == expected) { "Declared cash $declaredAmount must equal the selected collection total $expected." }
            val remittance = CashRemittance(
                remittanceNumber = BillingText.required(remittanceNumber, 60, "remittanceNumber").uppercase(),
                courierUserId = courierUserId,
                hubId = hubId,
                expectedAmount = expected,
                declaredAmount = declaredAmount,
                submittedAt = submittedAt,
                createdAt = submittedAt,
                updatedAt = submittedAt,
            )
            collections.sortedBy { it.collectedAt }.forEachIndexed { index, collection ->
                remittance.items += CashRemittanceItem(
                    remittance = remittance,
                    sequence = index + 1,
                    collectionId = collection.id,
                    amount = collection.amount,
                    createdAt = submittedAt,
                )
            }
            return remittance
        }
    }
}

@Entity
@Table(name = "cash_remittance_items", schema = "billing")
class CashRemittanceItem(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remittance_id", nullable = false)
    var remittance: CashRemittance = CashRemittance(),
    @Column(nullable = false)
    var sequence: Int = 1,
    @Column(name = "collection_id", nullable = false)
    var collectionId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var amount: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)
