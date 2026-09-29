package com.pavex.billing.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CustomerInvoiceTest {
    private val now = Instant.parse("2026-09-25T03:00:00Z")

    @Test
    fun `payments move the invoice from partially paid to paid`() {
        val invoice = invoice(30_000, 20_000)

        invoice.applyCompletedPayment(20_000, now)
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.status)
        assertEquals(30_000, invoice.balanceAmount())

        invoice.applyCompletedPayment(30_000, now)
        assertEquals(InvoiceStatus.PAID, invoice.status)
        assertEquals(now, invoice.paidAt)
        assertThrows(IllegalStateException::class.java) { invoice.applyCompletedPayment(1, now) }
    }

    @Test
    fun `an invoice with a completed payment cannot be cancelled`() {
        val invoice = invoice(50_000)
        invoice.addPayment(InvoicePayment.cash(invoice, 10_000, "CASH-1", UUID.randomUUID(), now))
        invoice.applyCompletedPayment(10_000, now)

        assertThrows(IllegalStateException::class.java) { invoice.cancel(UUID.randomUUID(), "Duplicate", now) }
    }

    @Test
    fun `a live QR code reserves the balance until it expires`() {
        val invoice = invoice(50_000)
        val qr = InvoicePayment.qrReservation(UUID.randomUUID(), invoice, 40_000, "INV-QR-1", "fake", now.plusSeconds(900), now)
        invoice.addPayment(qr)

        assertEquals(10_000, invoice.availablePaymentAmount(now))
        assertEquals(50_000, invoice.availablePaymentAmount(now, setOf(qr.id)))

        val later = now.plusSeconds(901)
        assertEquals(true, invoice.expirePendingQrPayments(later))
        assertEquals(InvoicePaymentStatus.EXPIRED, qr.status)
        assertEquals(50_000, invoice.availablePaymentAmount(later))
    }

    @Test
    fun `a shipment appears on one line only`() {
        val shipment = UUID.randomUUID()
        assertThrows(IllegalArgumentException::class.java) {
            CustomerInvoice.create(
                "INV-1",
                UUID.randomUUID(),
                listOf(InvoiceLineDraft(shipment, "PAVEX1", 10_000), InvoiceLineDraft(shipment, "PAVEX1", 10_000)),
                now,
                now,
            )
        }
    }

    private fun invoice(vararg amounts: Long) = CustomerInvoice.create(
        invoiceNumber = "INV-20260925-TEST",
        merchantId = UUID.randomUUID(),
        lines = amounts.mapIndexed { index, amount -> InvoiceLineDraft(UUID.randomUUID(), "PAVEX$index", amount) },
        issuedAt = now,
        dueAt = now.plusSeconds(7 * 86_400),
    )
}
