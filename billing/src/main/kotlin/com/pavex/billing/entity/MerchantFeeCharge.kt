package com.pavex.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** The shipping fee a merchant owes for one closed shipment, waiting to be settled. */
@Entity
@Table(name = "merchant_fee_charges", schema = "billing")
class MerchantFeeCharge(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "tracking_code", nullable = false, length = 40)
    var trackingCode: String = "",
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var amount: Long = 0,
    @Column(name = "shipping_fee_payer", nullable = false, length = 20)
    var shippingFeePayer: String = "",
    @Column(name = "final_status", nullable = false, length = 30)
    var finalStatus: String = "",
    @Column(name = "closed_at", nullable = false)
    var closedAt: Instant = Instant.EPOCH,
    @Column(name = "statement_id")
    var statementId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
) {
    fun includeInStatement(statementId: UUID) {
        check(this.statementId == null) { "Fee charge is already settled." }
        this.statementId = statementId
    }
}
