package com.pavex.billing.repository

import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.CashCollectionStatus
import com.pavex.billing.entity.CashRemittance
import com.pavex.billing.entity.CashRemittanceStatus
import com.pavex.billing.entity.CashSource
import com.pavex.billing.entity.CounterSettlement
import com.pavex.billing.entity.CounterSettlementKind
import com.pavex.billing.entity.CounterSettlementStatus
import com.pavex.billing.entity.CounterShipment
import com.pavex.billing.entity.CustomerInvoice
import com.pavex.billing.entity.InvoicePayment
import com.pavex.billing.entity.InvoiceStatus
import com.pavex.billing.entity.MerchantFeeCharge
import com.pavex.billing.entity.MerchantPayoutAccount
import com.pavex.billing.entity.PaymentWebhookReceipt
import com.pavex.billing.entity.ProcessedEvent
import com.pavex.billing.entity.SettlementStatement
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface CustomerInvoiceRepository : JpaRepository<CustomerInvoice, UUID> {
    @EntityGraph(attributePaths = ["lines"])
    fun findWithLinesById(id: UUID): CustomerInvoice?

    @Query(
        """
        select i from CustomerInvoice i
        where (:merchantId is null or i.merchantId = :merchantId)
          and (:status is null or i.status = :status)
        order by i.issuedAt desc
        """,
    )
    fun search(
        @Param("merchantId") merchantId: UUID?,
        @Param("status") status: InvoiceStatus?,
        pageable: Pageable,
    ): List<CustomerInvoice>
}

interface InvoicePaymentRepository : JpaRepository<InvoicePayment, UUID> {
    fun findByProviderAndProviderPaymentId(provider: String, providerPaymentId: String): InvoicePayment?

    @Query(
        """
        select p.id from InvoicePayment p
        where p.method = com.pavex.billing.entity.InvoicePaymentMethod.QR
          and p.status in (com.pavex.billing.entity.InvoicePaymentStatus.PENDING, com.pavex.billing.entity.InvoicePaymentStatus.EXPIRED)
          and p.provider is not null and p.providerPaymentId is not null
          and p.createdAt <= :cutoff
        order by p.updatedAt asc, p.id asc
        """,
    )
    fun findOpenQrPaymentIds(@Param("cutoff") cutoff: Instant, pageable: Pageable): List<UUID>
}

interface PaymentWebhookReceiptRepository : JpaRepository<PaymentWebhookReceipt, UUID> {
    fun findByProviderAndExternalEventId(provider: String, externalEventId: String): PaymentWebhookReceipt?
}

interface CashCollectionRepository : JpaRepository<CashCollection, UUID> {
    fun existsByShipmentIdAndSource(shipmentId: UUID, source: CashSource): Boolean

    fun findByCollectedByUserIdOrderByCollectedAtDesc(userId: UUID, pageable: Pageable): List<CashCollection>

    fun findByCollectedByUserIdAndStatusOrderByCollectedAtDesc(
        userId: UUID,
        status: CashCollectionStatus,
        pageable: Pageable,
    ): List<CashCollection>

    fun findByCurrentHubIdOrderByUpdatedAtDesc(hubId: UUID, pageable: Pageable): List<CashCollection>

    fun findByCurrentHubIdAndStatusOrderByUpdatedAtDesc(hubId: UUID, status: CashCollectionStatus, pageable: Pageable): List<CashCollection>

    fun findByStatementIdOrderByApprovedAtAsc(statementId: UUID): List<CashCollection>

    @Query(
        """
        select c from CashCollection c
        where (:hubId is null or c.currentHubId = :hubId or c.originHubId = :hubId)
          and (:collectorUserId is null or c.collectedByUserId = :collectorUserId)
          and (:status is null or c.status = :status)
        order by c.updatedAt desc
        """,
    )
    fun search(
        @Param("hubId") hubId: UUID?,
        @Param("collectorUserId") collectorUserId: UUID?,
        @Param("status") status: CashCollectionStatus?,
        pageable: Pageable,
    ): List<CashCollection>

    @Query(
        """
        select c from CashCollection c
        where c.status = com.pavex.billing.entity.CashCollectionStatus.APPROVED
          and c.statementId is null and c.codAmount > 0 and c.approvedAt <= :cutoff
          and c.codPayee = com.pavex.billing.entity.CodPayee.MERCHANT
          and (:merchantId is null or c.merchantId = :merchantId)
        order by c.approvedAt asc
        """,
    )
    fun findUnsettledCod(@Param("merchantId") merchantId: UUID?, @Param("cutoff") cutoff: Instant): List<CashCollection>
}

interface CashRemittanceRepository : JpaRepository<CashRemittance, UUID> {
    @EntityGraph(attributePaths = ["items"])
    fun findWithItemsById(id: UUID): CashRemittance?

    @EntityGraph(attributePaths = ["items"])
    @Query(
        """
        select distinct r from CashRemittance r
        where (:hubId is null or r.hubId = :hubId)
          and (:courierUserId is null or r.courierUserId = :courierUserId)
          and (:status is null or r.status = :status)
        order by r.submittedAt desc
        """,
    )
    fun search(
        @Param("hubId") hubId: UUID?,
        @Param("courierUserId") courierUserId: UUID?,
        @Param("status") status: CashRemittanceStatus?,
        pageable: Pageable,
    ): List<CashRemittance>
}

interface MerchantFeeChargeRepository : JpaRepository<MerchantFeeCharge, UUID> {
    fun existsByShipmentId(shipmentId: UUID): Boolean

    fun findByStatementIdOrderByClosedAtAsc(statementId: UUID): List<MerchantFeeCharge>

    @Query(
        """
        select f from MerchantFeeCharge f
        where f.statementId is null and f.closedAt <= :cutoff
          and (:merchantId is null or f.merchantId = :merchantId)
        order by f.closedAt asc
        """,
    )
    fun findUnsettled(@Param("merchantId") merchantId: UUID?, @Param("cutoff") cutoff: Instant): List<MerchantFeeCharge>
}

interface SettlementStatementRepository : JpaRepository<SettlementStatement, UUID> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: UUID, pageable: Pageable): List<SettlementStatement>

    @Query(
        """
        select s from SettlementStatement s
        where (:merchantId is null or s.merchantId = :merchantId)
          and (:payoutStatus is null or s.payoutStatus = :payoutStatus)
        order by s.createdAt desc
        """,
    )
    fun search(
        @Param("merchantId") merchantId: UUID?,
        @Param("payoutStatus") payoutStatus: com.pavex.billing.entity.PayoutStatus?,
        pageable: Pageable,
    ): List<SettlementStatement>
}

interface MerchantPayoutAccountRepository : JpaRepository<MerchantPayoutAccount, UUID>

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, UUID>

interface CounterShipmentRepository : JpaRepository<CounterShipment, UUID>

interface CounterSettlementRepository : JpaRepository<CounterSettlement, UUID> {
    fun existsByShipmentIdAndKind(shipmentId: UUID, kind: CounterSettlementKind): Boolean

    /** A hub's counter list, optionally narrowed to one sender phone or tracking code. */
    @Query(
        """
        select s from CounterSettlement s
        where (:hubId is null or s.hubId = :hubId)
          and (:status is null or s.status = :status)
          and (:phone is null or s.senderPhone = :phone)
          and (:trackingCode is null or s.trackingCode = :trackingCode)
        order by s.createdAt desc
        """,
    )
    fun search(
        @Param("hubId") hubId: UUID?,
        @Param("status") status: CounterSettlementStatus?,
        @Param("phone") phone: String?,
        @Param("trackingCode") trackingCode: String?,
        pageable: Pageable,
    ): List<CounterSettlement>
}
