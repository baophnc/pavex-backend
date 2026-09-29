package com.pavex.shipment.repository

import com.pavex.shipment.entity.Shipment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ShipmentRepository : JpaRepository<Shipment, UUID> {
    @Query(
        """
        select s.id as id, s.routeRevision as routeRevision, s.createdByUserId as createdByUserId
        from Shipment s
        where s.networkConfirmedAt is null
          and s.networkConfirmationError is null
          and s.capacityReservationBatchId is not null
          and s.cancelledAt is null
          and s.createdAt >= :createdAfter
        order by s.createdAt asc
        """,
    )
    fun findPendingNetworkConfirmations(
        @Param("createdAfter") createdAfter: Instant,
        pageable: Pageable,
    ): List<PendingNetworkConfirmation>

    // Bulk updates bypass @Version on purpose: these two columns are only written here,
    // and bumping the version would make concurrent scan/cancel requests fail spuriously.
    @Modifying
    @Query("update Shipment s set s.networkConfirmedAt = :now where s.id = :id and s.networkConfirmedAt is null")
    fun markNetworkConfirmed(@Param("id") id: UUID, @Param("now") now: Instant): Int

    @Modifying
    @Query(
        "update Shipment s set s.networkConfirmationError = :error " +
            "where s.id = :id and s.networkConfirmedAt is null",
    )
    fun markNetworkConfirmationFailed(@Param("id") id: UUID, @Param("error") error: String): Int

    fun findByTrackingCodeIgnoreCase(trackingCode: String): Shipment?
    fun findByTrackingCodeIgnoreCaseAndCreatedByUserId(trackingCode: String, createdByUserId: UUID): Shipment?
    fun findByQuoteId(quoteId: UUID): Shipment?
    fun findByCreatedByUserIdAndIdempotencyKey(createdByUserId: UUID, idempotencyKey: String): Shipment?
    fun existsByQuoteId(quoteId: UUID): Boolean

    /** Shipments of a merchant that still use a merchant reference; cancelled ones free it. */
    @Query(
        """
        select s.trackingCode from Shipment s
        where s.merchantId = :merchantId and s.merchantReference = :reference
          and s.lifecycleStatus <> com.pavex.shipment.entity.ShipmentLifecycleStatus.CANCELLED
        """,
    )
    fun findActiveTrackingCodesByMerchantReference(
        @Param("merchantId") merchantId: UUID,
        @Param("reference") reference: String,
    ): List<String>
}

interface PendingNetworkConfirmation {
    val id: UUID
    val routeRevision: Int
    val createdByUserId: UUID
}
