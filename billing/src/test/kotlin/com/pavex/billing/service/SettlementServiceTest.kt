package com.pavex.billing.service

import com.pavex.billing.client.PartnersClient
import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.CustomerInvoice
import com.pavex.billing.entity.InvoicePaymentMethod
import com.pavex.billing.entity.InvoiceStatus
import com.pavex.billing.entity.MerchantFeeCharge
import com.pavex.billing.entity.PayoutStatus
import com.pavex.billing.entity.SettlementStatement
import com.pavex.billing.exception.InvalidBillingRequestException
import com.pavex.billing.repository.CashCollectionRepository
import com.pavex.billing.repository.CustomerInvoiceRepository
import com.pavex.billing.repository.MerchantFeeChargeRepository
import com.pavex.billing.repository.MerchantPayoutAccountRepository
import com.pavex.billing.repository.SettlementStatementRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class SettlementServiceTest {
    private val now = Instant.parse("2026-09-25T05:00:00Z")
    private val merchant = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val fees = mutableListOf<MerchantFeeCharge>()
    private val cod = mutableListOf<CashCollection>()
    private val invoices = mutableMapOf<UUID, CustomerInvoice>()
    private val statements = mutableMapOf<UUID, SettlementStatement>()
    private val service = SettlementService(
        statements = mock(SettlementStatementRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "saveAndFlush" -> invocation.getArgument<SettlementStatement>(0).also { statements[it.id] = it }
                else -> null
            }
        },
        feeCharges = mock(MerchantFeeChargeRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findUnsettled" -> fees.filter { it.statementId == null && it.merchantId == invocation.getArgument(0) }
                "saveAllAndFlush" -> invocation.getArgument<Any>(0)
                else -> null
            }
        },
        collections = mock(CashCollectionRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findUnsettledCod" -> cod.filter { it.statementId == null && it.merchantId == invocation.getArgument(0) }
                "saveAllAndFlush" -> invocation.getArgument<Any>(0)
                else -> null
            }
        },
        invoices = mock(CustomerInvoiceRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "saveAndFlush" -> invocation.getArgument<CustomerInvoice>(0).also { invoices[it.id] = it }
                "findById" -> Optional.ofNullable(invoices[invocation.getArgument(0)])
                else -> null
            }
        },
        payoutAccounts = mock(MerchantPayoutAccountRepository::class.java),
        partners = mock(PartnersClient::class.java),
        transactionManager = NoTransactions,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        invoiceDueDays = 7,
    )

    @Test
    fun `COD above the fees pays the invoice in full and leaves a payout`() {
        fee(30_000)
        fee(20_000)
        approvedCod(300_000)

        val statement = service.generate(actor, cutoffAt = null, merchantId = merchant).single()

        assertEquals(50_000, statement.feeTotal)
        assertEquals(300_000, statement.codTotal)
        assertEquals(250_000, statement.payoutAmount)
        assertEquals(PayoutStatus.PENDING, statement.payoutStatus)
        val invoice = invoices.values.single()
        assertEquals(InvoiceStatus.PAID, invoice.status)
        assertEquals(listOf(InvoicePaymentMethod.COD_OFFSET), invoice.payments.map { it.method })
        assertTrue(fees.all { it.statementId == statement.id } && cod.all { it.statementId == statement.id })

        assertTrue(service.generate(actor, cutoffAt = null, merchantId = merchant).isEmpty())
    }

    @Test
    fun `fees above the COD leave a balance on the invoice and no payout`() {
        fee(50_000)
        approvedCod(20_000)

        val statement = service.generate(actor, cutoffAt = null, merchantId = merchant).single()

        assertEquals(0, statement.payoutAmount)
        assertEquals(PayoutStatus.NOT_REQUIRED, statement.payoutStatus)
        val invoice = invoices.values.single()
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.status)
        assertEquals(30_000, invoice.balanceAmount())
    }

    @Test
    fun `COD alone is paid out without an invoice`() {
        approvedCod(120_000)

        val statement = service.generate(actor, cutoffAt = null, merchantId = merchant).single()

        assertEquals(120_000, statement.payoutAmount)
        assertEquals(null, statement.invoice)
        assertTrue(invoices.isEmpty())
    }

    @Test
    fun `a cutoff in the future is refused`() {
        assertThrows(InvalidBillingRequestException::class.java) {
            service.generate(actor, cutoffAt = now.plusSeconds(60), merchantId = null)
        }
    }

    private fun fee(amount: Long) {
        fees += MerchantFeeCharge(
            shipmentId = UUID.randomUUID(),
            trackingCode = "PAVEX26092501234567${fees.size}",
            merchantId = merchant,
            amount = amount,
            shippingFeePayer = "SENDER",
            finalStatus = "DELIVERED",
            closedAt = now.minusSeconds(3_600),
            createdAt = now,
        )
    }

    private fun approvedCod(amount: Long) {
        cod += CashCollection.fromDelivery(
            shipmentId = UUID.randomUUID(),
            trackingCode = "PAVEX26092509876543${cod.size}",
            merchantId = merchant,
            deliveryAttemptId = UUID.randomUUID(),
            courierUserId = UUID.randomUUID(),
            hubId = UUID.randomUUID(),
            codAmount = amount,
            feeAmount = 0,
            collectedAt = now.minusSeconds(7_200),
        ).apply {
            submitToHub(UUID.randomUUID(), originHubId, now.minusSeconds(3_600))
            approve(UUID.randomUUID(), now.minusSeconds(1_800))
        }
    }

    private object NoTransactions : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }
}
