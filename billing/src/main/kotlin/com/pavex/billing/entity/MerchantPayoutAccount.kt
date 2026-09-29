package com.pavex.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/** The bank account Pavex transfers a merchant's COD payouts to. */
@Entity
@Table(name = "merchant_payout_accounts", schema = "billing")
class MerchantPayoutAccount(
    @Id
    @Column(name = "merchant_id")
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "bank_name", nullable = false, length = 100)
    var bankName: String = "",
    @Column(name = "account_number", nullable = false, length = 40)
    var accountNumber: String = "",
    @Column(name = "account_name", nullable = false, length = 200)
    var accountName: String = "",
    @Column(name = "updated_by_user_id", nullable = false)
    var updatedByUserId: UUID = UUID.randomUUID(),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0,
) {
    fun update(bankName: String, accountNumber: String, accountName: String, actorUserId: UUID, now: Instant) {
        this.bankName = BillingText.required(bankName, 100, "bankName")
        this.accountNumber = normalizeAccountNumber(accountNumber)
        this.accountName = BillingText.required(accountName, 200, "accountName").uppercase()
        updatedByUserId = actorUserId
        updatedAt = now
    }

    companion object {
        private val ACCOUNT_NUMBER = Regex("^[0-9]{6,20}$")

        fun normalizeAccountNumber(value: String): String {
            val digits = value.filterNot { it.isWhitespace() || it == '-' || it == '.' }
            require(ACCOUNT_NUMBER.matches(digits)) { "Account number must contain 6 to 20 digits." }
            return digits
        }
    }
}
