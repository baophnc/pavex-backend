package com.pavex.billing.service

import com.pavex.billing.client.PartnersClient
import com.pavex.billing.dto.PayoutAccountResponse
import com.pavex.billing.dto.SettlementCodLineResponse
import com.pavex.billing.dto.SettlementFeeLineResponse
import com.pavex.billing.dto.SettlementStatementDetailResponse
import com.pavex.billing.dto.SettlementStatementResponse
import com.pavex.billing.dto.UpdatePayoutAccountRequest
import com.pavex.billing.entity.CustomerInvoice
import com.pavex.billing.entity.InvoiceLineDraft
import com.pavex.billing.entity.InvoicePayment
import com.pavex.billing.entity.MerchantPayoutAccount
import com.pavex.billing.entity.PayoutStatus
import com.pavex.billing.entity.SettlementStatement
import com.pavex.billing.exception.BillingConflictException
import com.pavex.billing.exception.BillingNotFoundException
import com.pavex.billing.exception.InvalidBillingRequestException
import com.pavex.billing.repository.CashCollectionRepository
import com.pavex.billing.repository.CustomerInvoiceRepository
import com.pavex.billing.repository.MerchantFeeChargeRepository
import com.pavex.billing.repository.MerchantPayoutAccountRepository
import com.pavex.billing.repository.SettlementStatementRepository
import com.pavex.billing.service.InvoiceService.Companion.domain
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Periodic COD settlement. For each merchant, the shipping fees closed up to the cutoff become
 * one invoice, and the COD finance approved up to the cutoff is offset against it:
 * COD left over is paid out to the merchant, fees left over stay on the invoice to be paid.
 */
@Service
class SettlementService(
    private val statements: SettlementStatementRepository,
    private val feeCharges: MerchantFeeChargeRepository,
    private val collections: CashCollectionRepository,
    private val invoices: CustomerInvoiceRepository,
    private val payoutAccounts: MerchantPayoutAccountRepository,
    private val partners: PartnersClient,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
    @Value("\${billing.settlement.invoice-due-days:7}") private val invoiceDueDays: Long,
) {
    private val tx = TransactionTemplate(transactionManager)
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /** Creates one statement per merchant with anything unsettled; each merchant commits on its own. */
    fun generate(actorUserId: UUID, cutoffAt: Instant?, merchantId: UUID?): List<SettlementStatementResponse> {
        val now = clock.instant()
        val cutoff = cutoffAt ?: now
        if (cutoff.isAfter(now)) throw InvalidBillingRequestException("invalid_cutoff", "The settlement cutoff cannot be in the future.")
        val merchants = inReadTx {
            (feeCharges.findUnsettled(merchantId, cutoff).map { it.merchantId } +
                collections.findUnsettledCod(merchantId, cutoff).mapNotNull { it.merchantId }).distinct()
        }
        return merchants.mapNotNull { merchant -> inTx { settleMerchant(merchant, cutoff, actorUserId) } }
    }

    fun list(merchantId: UUID?, payoutStatus: PayoutStatus?): List<SettlementStatementResponse> = inReadTx {
        statements.search(merchantId, payoutStatus, PageRequest.of(0, LIST_LIMIT)).map(::summary)
    }

    fun listMine(userId: UUID): List<SettlementStatementResponse> {
        val merchantId = partners.requireMerchantId(userId)
        return inReadTx { statements.findByMerchantIdOrderByCreatedAtDesc(merchantId, PageRequest.of(0, LIST_LIMIT)).map(::summary) }
    }

    fun get(statementId: UUID): SettlementStatementDetailResponse = inReadTx { detail(load(statementId)) }

    fun getMine(userId: UUID, statementId: UUID): SettlementStatementDetailResponse {
        val merchantId = partners.requireMerchantId(userId)
        return inReadTx {
            val statement = load(statementId).takeIf { it.merchantId == merchantId }
                ?: throw BillingNotFoundException("statement_not_found", "Settlement statement was not found.")
            detail(statement)
        }
    }

    /** Records the bank transfer of a statement's payout to the merchant's registered account. */
    fun recordPayout(statementId: UUID, reference: String, actorUserId: UUID): SettlementStatementResponse = inTx {
        val statement = load(statementId)
        val account = payoutAccounts.findById(statement.merchantId).orElseThrow {
            BillingConflictException("payout_account_missing", "The merchant has not registered a payout bank account.")
        }
        domain("payout_conflict") { statement.recordPayout(reference, account, actorUserId, clock.instant()) }
        summary(statements.saveAndFlush(statement))
    }

    fun payoutAccountOf(merchantId: UUID): PayoutAccountResponse = inReadTx {
        payoutAccounts.findById(merchantId).map(PayoutAccountResponse::from).orElseThrow {
            BillingNotFoundException("payout_account_not_found", "No payout account is registered.")
        }
    }

    fun myPayoutAccount(userId: UUID): PayoutAccountResponse = payoutAccountOf(partners.requireMerchantId(userId))

    fun updateMyPayoutAccount(userId: UUID, request: UpdatePayoutAccountRequest): PayoutAccountResponse {
        val merchantId = partners.requireMerchantId(userId)
        return inTx {
            val now = clock.instant()
            val account = payoutAccounts.findById(merchantId).orElseGet {
                MerchantPayoutAccount(merchantId = merchantId, createdAt = now)
            }
            domain("payout_account_conflict") { account.update(request.bankName, request.accountNumber, request.accountName, userId, now) }
            PayoutAccountResponse.from(payoutAccounts.saveAndFlush(account))
        }
    }

    private fun settleMerchant(merchantId: UUID, cutoff: Instant, actorUserId: UUID): SettlementStatementResponse? {
        val fees = feeCharges.findUnsettled(merchantId, cutoff).take(CustomerInvoice.MAX_LINES)
        val cod = collections.findUnsettledCod(merchantId, cutoff)
        if (fees.isEmpty() && cod.isEmpty()) return null

        val now = clock.instant()
        val statementNumber = number("ST")
        val feeTotal = fees.fold(0L) { total, fee -> Math.addExact(total, fee.amount) }
        val codTotal = cod.fold(0L) { total, collection -> Math.addExact(total, collection.codAmount) }

        val invoice = fees.takeIf { it.isNotEmpty() }?.let {
            CustomerInvoice.create(
                invoiceNumber = number("INV"),
                merchantId = merchantId,
                lines = it.map { fee -> InvoiceLineDraft(fee.shipmentId, fee.trackingCode, fee.amount) },
                issuedAt = now,
                dueAt = now.plus(Duration.ofDays(invoiceDueDays)),
                note = "Cước vận chuyển kỳ đối soát $statementNumber",
            )
        }
        val statement = SettlementStatement.create(statementNumber, merchantId, cutoff, codTotal, feeTotal, invoice?.id, actorUserId, now)
        if (invoice != null && statement.offsetAmount > 0) {
            invoice.addPayment(InvoicePayment.codOffset(invoice, statement.offsetAmount, "OFFSET-$statementNumber", now))
            invoice.applyCompletedPayment(statement.offsetAmount, now)
        }

        invoice?.let(invoices::saveAndFlush)
        statements.saveAndFlush(statement)
        fees.forEach { it.includeInStatement(statement.id) }
        cod.forEach { it.includeInStatement(statement.id, now) }
        feeCharges.saveAllAndFlush(fees)
        collections.saveAllAndFlush(cod)
        return SettlementStatementResponse.from(statement, invoice)
    }

    private fun summary(statement: SettlementStatement) =
        SettlementStatementResponse.from(statement, statement.invoiceId?.let { invoices.findById(it).orElse(null) })

    private fun detail(statement: SettlementStatement) = SettlementStatementDetailResponse(
        statement = summary(statement),
        codLines = collections.findByStatementIdOrderByApprovedAtAsc(statement.id).map {
            SettlementCodLineResponse(it.shipmentId, it.trackingCode, it.codAmount, it.collectedAt, it.approvedAt)
        },
        feeLines = feeCharges.findByStatementIdOrderByClosedAtAsc(statement.id).map(SettlementFeeLineResponse::from),
    )

    private fun load(statementId: UUID): SettlementStatement =
        statements.findById(statementId).orElseThrow { BillingNotFoundException("statement_not_found", "Settlement statement was not found.") }

    private fun number(prefix: String): String =
        "$prefix-${DAY.format(clock.instant().atOffset(ZoneOffset.UTC))}-${InvoiceService.compactId(UUID.randomUUID()).take(8)}"

    private fun <T> inTx(block: () -> T): T = tx.execute { block() } as T

    private fun <T> inReadTx(block: () -> T): T = readTx.execute { block() } as T

    companion object {
        private const val LIST_LIMIT = 200
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
