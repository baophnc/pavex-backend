package com.pavex.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** One shipment's shipping fee on an invoice. */
@Entity
@Table(name = "customer_invoice_lines", schema = "billing")
class InvoiceLine(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    var invoice: CustomerInvoice = CustomerInvoice(),
    @Column(nullable = false)
    var sequence: Int = 1,
    @Column(name = "shipment_id", nullable = false)
    var shipmentId: UUID = UUID.randomUUID(),
    @Column(name = "tracking_code", nullable = false, length = 40)
    var trackingCode: String = "",
    @Column(nullable = false)
    var amount: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
) {
    companion object {
        fun create(invoice: CustomerInvoice, sequence: Int, draft: InvoiceLineDraft, createdAt: Instant): InvoiceLine {
            require(sequence > 0) { "sequence must be positive." }
            require(draft.amount > 0) { "Invoice line amount must be positive." }
            return InvoiceLine(
                invoice = invoice,
                sequence = sequence,
                shipmentId = draft.shipmentId,
                trackingCode = BillingText.required(draft.trackingCode, 40, "trackingCode").uppercase(),
                amount = draft.amount,
                createdAt = createdAt,
            )
        }
    }
}
