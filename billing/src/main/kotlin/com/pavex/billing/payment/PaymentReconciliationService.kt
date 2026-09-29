package com.pavex.billing.payment

import com.pavex.billing.entity.InvoicePayment
import com.pavex.billing.entity.InvoicePaymentStatus
import com.pavex.billing.repository.CustomerInvoiceRepository
import com.pavex.billing.repository.InvoicePaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.UUID

data class PaymentReconciliationSummary(val examined: Int, val settled: Int, val cancelled: Int, val pending: Int, val failed: Int)

/**
 * Asks the provider about QR payments that are still open, in case a callback never arrived
 * (ported from core-api). Only one payment is handled per transaction.
 */
@Service
class PaymentReconciliationService(
    private val payments: InvoicePaymentRepository,
    private val invoices: CustomerInvoiceRepository,
    private val gateways: PaymentGatewayResolver,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
    @Value("\${billing.payments.reconciliation.minimum-age:PT30S}") private val minimumAge: Duration,
    @Value("\${billing.payments.reconciliation.batch-size:50}") private val batchSize: Int,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun reconcile(): PaymentReconciliationSummary {
        val now = clock.instant()
        val ids = payments.findOpenQrPaymentIds(now.minus(minimumAge), PageRequest.of(0, batchSize.coerceIn(1, 500)))
        var settled = 0
        var cancelled = 0
        var pending = 0
        var failed = 0
        ids.forEach { id ->
            when (runCatching { reconcileOne(id) }.getOrElse { exception ->
                if (exception !is ObjectOptimisticLockingFailureException && exception !is PaymentGatewayException) {
                    log.warn("Payment reconciliation failed for {}.", id, exception)
                }
                Result.FAILED
            }) {
                Result.SETTLED -> settled++
                Result.CANCELLED -> cancelled++
                Result.PENDING -> pending++
                Result.FAILED -> failed++
            }
        }
        return PaymentReconciliationSummary(ids.size, settled, cancelled, pending, failed)
    }

    private fun reconcileOne(paymentId: UUID): Result {
        val lookup = tx.execute {
            val payment = payments.findById(paymentId).orElse(null) ?: return@execute null
            if (!isOpen(payment)) return@execute null
            Triple(requireNotNull(payment.provider), PaymentGatewayLookupRequest(payment.id, payment.providerPaymentId!!, payment.providerReference), payment.amount)
        } ?: return Result.PENDING
        val (provider, request, amount) = lookup
        val gateway = runCatching { gateways.get(provider) }.getOrNull() ?: return Result.FAILED
        val remote = gateway.getPayment(request)
        if (remote.providerPaymentId != request.providerPaymentId || remote.amount != amount ||
            (request.providerReference != null && remote.providerReference != null && remote.providerReference != request.providerReference)
        ) {
            return Result.FAILED
        }

        return tx.execute {
            val payment = payments.findById(paymentId).orElseThrow()
            if (!isOpen(payment)) return@execute Result.PENDING
            val invoice = invoices.findWithLinesById(payment.invoice.id) ?: return@execute Result.FAILED
            val now = clock.instant()
            when (remote.status) {
                PaymentGatewayPaymentStatus.PAID -> {
                    if (remote.amountPaid < payment.amount || payment.amount > invoice.availablePaymentAmount(now, setOf(payment.id))) {
                        return@execute Result.FAILED
                    }
                    payment.completeFromProvider(now)
                    invoice.applyCompletedPayment(payment.amount, now)
                    invoices.saveAndFlush(invoice)
                    Result.SETTLED
                }
                PaymentGatewayPaymentStatus.CANCELLED -> {
                    payment.cancelFromProvider(now)
                    payments.saveAndFlush(payment)
                    Result.CANCELLED
                }
                else -> {
                    if (payment.expireIfNeeded(now)) payments.saveAndFlush(payment)
                    Result.PENDING
                }
            }
        }!!
    }

    private fun isOpen(payment: InvoicePayment): Boolean =
        (payment.status == InvoicePaymentStatus.PENDING || payment.status == InvoicePaymentStatus.EXPIRED) &&
            payment.isGatewayInitialized()

    private enum class Result { SETTLED, CANCELLED, PENDING, FAILED }

    companion object {
        private val log = LoggerFactory.getLogger(PaymentReconciliationService::class.java)
    }
}

@Component
class PaymentReconciliationWorker(
    private val reconciliation: PaymentReconciliationService,
    @Value("\${billing.payments.reconciliation.enabled:false}") private val enabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${billing.payments.reconciliation.interval:PT2M}")
    fun run() {
        if (!enabled) return
        try {
            val summary = reconciliation.reconcile()
            if (summary.failed > 0) log.warn("Payment reconciliation: {}", summary)
        } catch (exception: Exception) {
            log.error("Payment reconciliation cycle failed.", exception)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PaymentReconciliationWorker::class.java)
    }
}
