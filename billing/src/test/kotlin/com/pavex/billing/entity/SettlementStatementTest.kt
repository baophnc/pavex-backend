package com.pavex.billing.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SettlementStatementTest {
    private val now = Instant.parse("2026-09-25T03:00:00Z")

    @Test
    fun `COD above the fees is offset and the rest is paid out`() {
        val statement = statement(codTotal = 300_000, feeTotal = 50_000, invoiceId = UUID.randomUUID())

        assertEquals(50_000, statement.offsetAmount)
        assertEquals(250_000, statement.payoutAmount)
        assertEquals(PayoutStatus.PENDING, statement.payoutStatus)
    }

    @Test
    fun `fees above the COD leave nothing to pay out`() {
        val statement = statement(codTotal = 20_000, feeTotal = 50_000, invoiceId = UUID.randomUUID())

        assertEquals(20_000, statement.offsetAmount)
        assertEquals(0, statement.payoutAmount)
        assertEquals(PayoutStatus.NOT_REQUIRED, statement.payoutStatus)
    }

    @Test
    fun `fees are settled through exactly one invoice`() {
        assertThrows(IllegalArgumentException::class.java) { statement(codTotal = 0, feeTotal = 50_000, invoiceId = null) }
        assertThrows(IllegalArgumentException::class.java) { statement(codTotal = 50_000, feeTotal = 0, invoiceId = UUID.randomUUID()) }
    }

    @Test
    fun `a payout is recorded once against the merchant's own account`() {
        val merchant = UUID.randomUUID()
        val statement = statement(codTotal = 100_000, feeTotal = 0, invoiceId = null, merchantId = merchant)
        val account = MerchantPayoutAccount(merchantId = merchant).apply { update("Vietcombank", "0071 0000 12345", "nguyen van a", UUID.randomUUID(), now) }

        statement.recordPayout("FT26268ABC", account, UUID.randomUUID(), now)

        assertEquals(PayoutStatus.PAID, statement.payoutStatus)
        assertEquals("0071000012345", statement.payoutAccountNumber)
        assertEquals("NGUYEN VAN A", statement.payoutAccountName)
        assertThrows(IllegalStateException::class.java) { statement.recordPayout("again", account, UUID.randomUUID(), now) }
    }

    private fun statement(codTotal: Long, feeTotal: Long, invoiceId: UUID?, merchantId: UUID = UUID.randomUUID()) =
        SettlementStatement.create("ST-1", merchantId, now, codTotal, feeTotal, invoiceId, UUID.randomUUID(), now)
}
