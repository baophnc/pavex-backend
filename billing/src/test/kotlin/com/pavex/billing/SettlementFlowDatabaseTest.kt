package com.pavex.billing

import com.pavex.billing.client.OperationsClient
import com.pavex.billing.entity.CashCollectionStatus
import com.pavex.billing.entity.InvoicePaymentMethod
import com.pavex.billing.entity.InvoiceStatus
import com.pavex.billing.entity.PayoutStatus
import com.pavex.billing.messaging.BillingEventPublisher
import com.pavex.billing.messaging.ShipmentEventListener
import com.pavex.billing.service.CashService
import com.pavex.billing.service.InvoiceService
import com.pavex.billing.service.SettlementService
import com.pavex.contracts.operations.OperationsCapability
import com.pavex.test.PostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The money path of a merchant's shipments on a real database: the shipment events (as the
 * shipment outbox publishes them) reach the listener, the courier hands the cash to the hub,
 * finance approves it, and the settlement offsets the COD against the shipping fees.
 */
@PostgresIntegrationTest
class SettlementFlowDatabaseTest {
    @Autowired
    private lateinit var listener: ShipmentEventListener

    @Autowired
    private lateinit var cash: CashService

    @Autowired
    private lateinit var settlement: SettlementService

    @Autowired
    private lateinit var invoices: InvoiceService

    @MockitoBean
    private lateinit var operations: OperationsClient

    @MockitoBean
    private lateinit var billingEvents: BillingEventPublisher

    private val merchant = UUID.randomUUID()
    private val courier = UUID.randomUUID()
    private val hub = UUID.randomUUID()
    private val finance = UUID.randomUUID()
    private val deliveredAt = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS)

    @Test
    fun `COD collected on delivery pays the merchant's shipping fees and the rest is paid out`() {
        // Sender pays 30 000; recipient paid 250 000 COD.
        val senderPaid = shipment()
        val cashEvent = deliver(senderPaid, codAmount = 250_000, recipientFee = 0)
        close(senderPaid, "DELIVERED", "SENDER", shippingFee = 30_000, merchantFee = 30_000, codAmount = 250_000, collected = 250_000)
        // Recipient pays 22 000 with the 100 000 COD: the fee is Pavex's, the merchant owes nothing.
        val recipientPaid = shipment()
        deliver(recipientPaid, codAmount = 100_000, recipientFee = 22_000)
        close(recipientPaid, "DELIVERED", "RECIPIENT", shippingFee = 22_000, merchantFee = 0, codAmount = 100_000, collected = 122_000)
        // Returned: nothing collected, and the merchant pays the 25 000 the recipient never paid.
        val returned = shipment()
        close(returned, "RETURNED", "RECIPIENT", shippingFee = 25_000, merchantFee = 25_000, codAmount = 400_000, collected = null)

        // RabbitMQ redelivers a message, and a retried outbox row comes with a new event id: neither counts twice.
        deliver(senderPaid, codAmount = 250_000, recipientFee = 0, eventId = cashEvent)
        deliver(senderPaid, codAmount = 250_000, recipientFee = 0)

        val held = cash.mine(courier, CashCollectionStatus.HELD)
        assertEquals(listOf(122_000L, 250_000L), held.map { it.amount }.sorted())
        handToHubAndApprove(held.map { it.id }, declared = 372_000)

        val statement = settlement.generate(finance, null, merchant).single()
        assertEquals(350_000, statement.codTotal)
        assertEquals(55_000, statement.feeTotal)
        assertEquals(55_000, statement.offsetAmount)
        assertEquals(295_000, statement.payoutAmount)
        assertEquals(PayoutStatus.PENDING, statement.payoutStatus)

        val invoice = invoices.get(requireNotNull(statement.invoice).id)
        assertEquals(InvoiceStatus.PAID, invoice.status)
        assertEquals(55_000, invoice.totalAmount)
        assertEquals(0, invoice.balanceAmount)
        assertEquals(
            setOf(senderPaid.code to 30_000L, returned.code to 25_000L),
            invoice.lines.map { it.trackingCode to it.amount }.toSet(),
        )
        assertEquals(listOf(InvoicePaymentMethod.COD_OFFSET to 55_000L), invoice.payments.map { it.method to it.amount })

        val detail = settlement.get(statement.id)
        assertEquals(setOf(senderPaid.code, recipientPaid.code), detail.codLines.map { it.trackingCode }.toSet())
        assertEquals(setOf(senderPaid.code, returned.code), detail.feeLines.map { it.trackingCode }.toSet())

        assertTrue(settlement.generate(finance, null, merchant).isEmpty(), "Everything is settled once.")
    }

    @Test
    fun `fees larger than the COD stay on the invoice and nothing is paid out`() {
        val small = shipment()
        deliver(small, codAmount = 10_000, recipientFee = 0)
        close(small, "DELIVERED", "SENDER", shippingFee = 35_000, merchantFee = 35_000, codAmount = 10_000, collected = 10_000)
        handToHubAndApprove(cash.mine(courier, CashCollectionStatus.HELD).map { it.id }, declared = 10_000)

        val statement = settlement.generate(finance, null, merchant).single()
        assertEquals(10_000, statement.offsetAmount)
        assertEquals(0, statement.payoutAmount)
        assertEquals(PayoutStatus.NOT_REQUIRED, statement.payoutStatus)
        val invoice = invoices.get(requireNotNull(statement.invoice).id)
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.status)
        assertEquals(25_000, invoice.balanceAmount)
    }

    private fun handToHubAndApprove(collectionIds: List<UUID>, declared: Long) {
        doReturn(true).`when`(operations).canOperateAtHub(courier, hub, OperationsCapability.LAST_MILE_DELIVERY)
        val remittance = cash.submitRemittance(courier, hub, collectionIds, declared)
        cash.approveRemittance(remittance.id, finance)
    }

    private class TestShipment(val id: UUID, val code: String)

    private fun shipment(): TestShipment {
        val id = UUID.randomUUID()
        return TestShipment(id, "PAVEX${id.toString().replace("-", "").take(12).uppercase()}")
    }

    /** shipment.cash-collected, field for field as ShipmentFinancialEvents.cashCollected writes it. */
    private fun deliver(shipment: TestShipment, codAmount: Long, recipientFee: Long, eventId: UUID = UUID.randomUUID()): UUID {
        publish(
            "shipment.cash-collected",
            eventId,
            """
            {"shipmentId":"${shipment.id}","trackingCode":"${shipment.code}","merchantId":"$merchant",
             "channel":"MERCHANT_WEB","originHubId":"${UUID.randomUUID()}","courierUserId":"$courier",
             "hubId":"$hub","attemptId":"${UUID.randomUUID()}","amount":${codAmount + recipientFee},
             "codAmount":$codAmount,"recipientShippingFee":$recipientFee,"collectedAt":"$deliveredAt"}
            """,
        )
        return eventId
    }

    /** shipment.closed, field for field as ShipmentFinancialEvents.closedIfChanged writes it. */
    private fun close(
        shipment: TestShipment,
        finalStatus: String,
        payer: String,
        shippingFee: Long,
        merchantFee: Long,
        codAmount: Long,
        collected: Long?,
    ) = publish(
        "shipment.closed",
        UUID.randomUUID(),
        """
        {"shipmentId":"${shipment.id}","trackingCode":"${shipment.code}","merchantId":"$merchant",
         "channel":"MERCHANT_WEB","originHubId":"${UUID.randomUUID()}","lifecycleStatus":"COMPLETED",
         "finalStatus":"$finalStatus","shippingFeePayer":"$payer","shippingFee":$shippingFee,
         "merchantShippingFee":$merchantFee,"counterSenderFeeDue":0,"codAmount":$codAmount,
         "amountToCollect":${if (payer == "RECIPIENT") codAmount + shippingFee else codAmount},
         "collectedAmount":${collected ?: "null"},"closedAt":"${deliveredAt.plusSeconds(60)}"}
        """,
    )

    /** The message RabbitShipmentEventPublisher sends, handed to the listener as the broker would. */
    private fun publish(eventType: String, eventId: UUID, json: String) {
        val properties = MessageProperties().apply {
            messageId = eventId.toString()
            contentType = "application/json"
            contentEncoding = "UTF-8"
            receivedRoutingKey = eventType
            setHeader("X-Pavex-Event-Id", eventId.toString())
            setHeader("X-Pavex-Event-Type", eventType)
            setHeader("X-Pavex-Event-Version", 1)
            setHeader("X-Pavex-Producer", "shipment-service")
        }
        listener.onShipmentEvent(Message(json.trimIndent().toByteArray(), properties))
    }
}
