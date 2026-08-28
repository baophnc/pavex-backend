package com.pavex.partners.repository

import com.pavex.partners.entity.Merchant
import com.pavex.partners.entity.MerchantPickupAddress
import com.pavex.partners.entity.MerchantRoleOutboxMessage
import com.pavex.partners.entity.MerchantStatus
import com.pavex.partners.entity.MerchantStatusHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
    fun findTop20ByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByOccurredAtAsc(
        availableAt: Instant,
    ): List<MerchantRoleOutboxMessage>
}
