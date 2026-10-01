package com.pavex.partners.repository

import com.pavex.partners.entity.Merchant
import com.pavex.partners.entity.MerchantPickupAddress
import com.pavex.partners.entity.MerchantRoleOutboxMessage
import com.pavex.partners.entity.MerchantStatus
import com.pavex.partners.entity.MerchantStatusHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID
import java.time.Instant

interface MerchantRepository : JpaRepository<Merchant, UUID> {
    fun findByUserId(userId: UUID): Merchant?

    @Query(
        """
    SELECT m FROM Merchant m
    WHERE (:status IS NULL OR m.status = :status)
      AND (
           LOWER(m.merchantCode) LIKE LOWER(CONCAT('%', :search, '%')) OR
           LOWER(m.businessName) LIKE LOWER(CONCAT('%', :search, '%')) OR
           LOWER(COALESCE(m.taxCode, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
           LOWER(COALESCE(m.contactEmail, '')) LIKE LOWER(CONCAT('%', :search, '%'))
      )
    """,
    )
    fun search(
        @Param("status") status: MerchantStatus?,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<Merchant>
}

interface MerchantStatusHistoryRepository : JpaRepository<MerchantStatusHistory, UUID> {
    fun findAllByMerchantIdOrderByChangedAtDesc(merchantId: UUID): List<MerchantStatusHistory>
}

interface MerchantPickupAddressRepository : JpaRepository<MerchantPickupAddress, UUID> {
    fun findAllByMerchantIdOrderByIsDefaultDescCreatedAtAsc(merchantId: UUID): List<MerchantPickupAddress>
    fun findByIdAndMerchantId(id: UUID, merchantId: UUID): MerchantPickupAddress?
    fun findFirstByMerchantIdAndIsDefaultTrue(merchantId: UUID): MerchantPickupAddress?
    fun findFirstByMerchantIdOrderByCreatedAtAsc(merchantId: UUID): MerchantPickupAddress?
}

interface MerchantRoleOutboxRepository : JpaRepository<MerchantRoleOutboxMessage, UUID> {
    /** The merchant's role change still waiting to reach Identity, if any. */
    fun findFirstByMerchantIdAndProcessedAtIsNullOrderByOccurredAtDesc(merchantId: UUID): MerchantRoleOutboxMessage?

    /**
     * Claims due messages for this transaction. SKIP LOCKED lets several Partners
     * instances poll safely, and a message is only claimable when no older message for
     * the same user is still pending, so role changes reach Identity in order.
     */
    @Query(
        value = """
            SELECT m.*
            FROM partners.merchant_role_outbox m
            WHERE m.processed_at IS NULL
              AND m.available_at <= :now
              AND NOT EXISTS (
                  SELECT 1
                  FROM partners.merchant_role_outbox older
                  WHERE older.user_id = m.user_id
                    AND older.processed_at IS NULL
                    AND (older.occurred_at, older.id) < (m.occurred_at, m.id)
              )
            ORDER BY m.occurred_at ASC, m.id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun claimDispatchable(
        @Param("now") now: Instant,
        @Param("limit") limit: Int,
    ): List<MerchantRoleOutboxMessage>

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                FROM partners.merchant_role_outbox newer
                WHERE newer.user_id = :userId
                  AND (newer.occurred_at, newer.id) > (CAST(:occurredAt AS timestamptz), CAST(:id AS uuid))
            )
        """,
        nativeQuery = true,
    )
    fun existsNewerRoleChange(
        @Param("userId") userId: UUID,
        @Param("occurredAt") occurredAt: Instant,
        @Param("id") id: UUID,
    ): Boolean

    /**
     * Closes every still-pending role change of the user before a newer one is queued.
     * A row currently being dispatched is locked by the processor; this update waits for
     * that dispatch to commit and then no longer matches, which preserves ordering.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(
        value = """
            UPDATE partners.merchant_role_outbox
            SET superseded_at = :now,
                processed_at = :now,
                last_error = NULL
            WHERE user_id = :userId
              AND processed_at IS NULL
        """,
        nativeQuery = true,
    )
    fun supersedePendingRoleChanges(
        @Param("userId") userId: UUID,
        @Param("now") now: Instant,
    ): Int
}
