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

enum class PayoutStatus { NOT_REQUIRED, PENDING, PAID }

/**
 * One merchant's settlement for everything up to [cutoffAt]: COD collected is offset against the
 * shipping fees owed. What COD is left is paid out; what fee is left stays on the invoice.
 */
@Entity
@Table(name = "settlement_statements", schema = "billing")
class SettlementStatement(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "statement_number", nullable = false, length = 40)
    var statementNumber: String = "",
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "cutoff_at", nullable = false)
    var cutoffAt: Instant = Instant.EPOCH,
    @Column(name = "cod_total", nullable = false)
    var codTotal: Long = 0,
    @Column(name = "fee_total", nullable = false)
    var feeTotal: Long = 0,
    @Column(name = "offset_amount", nullable = false)
    var offsetAmount: Long = 0,
    @Column(name = "payout_amount", nullable = false)
    var payoutAmount: Long = 0,
    @Column(name = "invoice_id")
    var invoiceId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_status", nullable = false, length = 20)
    var payoutStatus: PayoutStatus = PayoutStatus.NOT_REQUIRED,
    @Column(name = "payout_reference", length = 100)
    var payoutReference: String? = null,
    @Column(name = "payout_bank_name", length = 100)
    var payoutBankName: String? = null,
    @Column(name = "payout_account_number", length = 40)
    var payoutAccountNumber: String? = null,
    @Column(name = "payout_account_name", length = 200)
    var payoutAccountName: String? = null,
    @Column(name = "paid_at")
    var paidAt: Instant? = null,
    @Column(name = "paid_by_user_id")
    var paidByUserId: UUID? = null,
    @Column(name = "created_by_user_id", nullable = false)
    var createdByUserId: UUID = UUID.randomUUID(),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    /** Records the bank transfer of [payoutAmount], snapshotting the account it was sent to. */
    fun recordPayout(reference: String, account: MerchantPayoutAccount, actorUserId: UUID, paidAt: Instant) {
        check(payoutStatus == PayoutStatus.PENDING) { "This statement has no pending payout." }
        check(account.merchantId == merchantId) { "The payout account belongs to another merchant." }
        payoutReference = BillingText.required(reference, 100, "reference")
        payoutBankName = account.bankName
        payoutAccountNumber = account.accountNumber
        payoutAccountName = account.accountName
        payoutStatus = PayoutStatus.PAID
        this.paidAt = paidAt
        paidByUserId = actorUserId
        updatedAt = paidAt
    }

    companion object {
        fun create(
            statementNumber: String,
            merchantId: UUID,
            cutoffAt: Instant,
            codTotal: Long,
            feeTotal: Long,
            invoiceId: UUID?,
            createdByUserId: UUID,
            createdAt: Instant,
        ): SettlementStatement {
            require(codTotal >= 0 && feeTotal >= 0) { "Settlement totals cannot be negative." }
            require(codTotal > 0 || feeTotal > 0) { "A settlement needs COD or fees to settle." }
            require((feeTotal > 0) == (invoiceId != null)) { "Fees are settled through exactly one invoice." }
            val offset = minOf(codTotal, feeTotal)
            val payout = codTotal - offset
            return SettlementStatement(
                statementNumber = BillingText.required(statementNumber, 40, "statementNumber").uppercase(),
                merchantId = merchantId,
                cutoffAt = cutoffAt,
                codTotal = codTotal,
                feeTotal = feeTotal,
                offsetAmount = offset,
                payoutAmount = payout,
                invoiceId = invoiceId,
                payoutStatus = if (payout > 0) PayoutStatus.PENDING else PayoutStatus.NOT_REQUIRED,
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }
    }
}
