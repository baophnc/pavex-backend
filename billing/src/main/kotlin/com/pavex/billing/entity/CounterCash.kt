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

/** A walk-in shipment booked at a hub counter: who sent it, and the counter that settles with them. */
@Entity
@Table(name = "counter_shipments", schema = "billing")
class CounterShipment(
    @Id
    @Column(name = "shipment_id")
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "tracking_code", nullable = false, length = 40)
    var trackingCode: String = "",
    @Column(name = "hub_id", nullable = false)
    var hubId: UUID = UUID.randomUUID(),
    @Column(name = "clerk_user_id", nullable = false)
    var clerkUserId: UUID = UUID.randomUUID(),
    @Column(name = "sender_name", nullable = false, length = 120)
    var senderName: String = "",
    @Column(name = "sender_phone", nullable = false, length = 20)
    var senderPhone: String = "",
    @Column(name = "shipping_fee_payer", nullable = false, length = 20)
    var shippingFeePayer: String = "SENDER",
    @Column(name = "shipping_fee", nullable = false)
    var shippingFee: Long = 0,
    @Column(name = "fee_paid_at_counter", nullable = false)
    var feePaidAtCounter: Long = 0,
    @Column(name = "cod_amount", nullable = false)
    var codAmount: Long = 0,
    @Column(name = "accepted_at", nullable = false)
    var acceptedAt: Instant = Instant.EPOCH,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)

/** PAYOUT_COD: the counter pays the sender their COD. COLLECT_RETURN_FEE: the sender pays a return's fee. */
enum class CounterSettlementKind { PAYOUT_COD, COLLECT_RETURN_FEE }

enum class CounterSettlementStatus { PENDING, DONE }

/**
 * Cash the counter of [hubId] owes a walk-in sender or is owed by them. Staff complete it when the
 * sender comes in, after checking the phone number the shipment was booked with.
 */
@Entity
@Table(name = "counter_settlements", schema = "billing")
class CounterSettlement(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "tracking_code", nullable = false, length = 40)
    var trackingCode: String = "",
    @Column(name = "hub_id", nullable = false)
    var hubId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var kind: CounterSettlementKind = CounterSettlementKind.PAYOUT_COD,
    @Column(nullable = false)
    var amount: Long = 0,
    @Column(name = "sender_name", nullable = false, length = 120)
    var senderName: String = "",
    @Column(name = "sender_phone", nullable = false, length = 20)
    var senderPhone: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CounterSettlementStatus = CounterSettlementStatus.PENDING,
    @Column(name = "completed_by_user_id")
    var completedByUserId: UUID? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(length = 500)
    var note: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    fun complete(actorUserId: UUID, note: String?, at: Instant) {
        check(status == CounterSettlementStatus.PENDING) { "This counter settlement is already done." }
        status = CounterSettlementStatus.DONE
        completedByUserId = actorUserId
        completedAt = at
        this.note = note?.trim()?.takeIf(String::isNotEmpty)?.take(500)
        updatedAt = at
    }

    companion object {
        fun of(counter: CounterShipment, kind: CounterSettlementKind, amount: Long, at: Instant): CounterSettlement {
            require(amount > 0) { "A counter settlement needs a positive amount." }
            return CounterSettlement(
                shipmentId = counter.shipmentId,
                trackingCode = counter.trackingCode,
                hubId = counter.hubId,
                kind = kind,
                amount = amount,
                senderName = counter.senderName,
                senderPhone = counter.senderPhone,
                createdAt = at,
                updatedAt = at,
            )
        }
    }
}
