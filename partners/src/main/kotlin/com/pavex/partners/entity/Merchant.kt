package com.pavex.partners.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class MerchantStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    REJECTED,
}

enum class MerchantStatusReason {
    DOCUMENT_INVALID,
    FRAUD_REVIEW,
    POLICY_VIOLATION,
    MERCHANT_REQUEST,
    OTHER,
}

@Entity
@Table(name = "merchants", schema = "partners")
class Merchant(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_code", nullable = false, length = 20, unique = true)
    var merchantCode: String = "",
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: UUID = UUID.randomUUID(),
    @Column(name = "business_name", nullable = false, length = 255)
    var businessName: String = "",
    @Column(name = "contact_name", nullable = false, length = 200)
    var contactName: String = "",
    @Column(name = "contact_phone", nullable = false, length = 30)
    var contactPhone: String = "",
    @Column(name = "contact_email", length = 320)
    var contactEmail: String? = null,
    @Column(name = "tax_code", length = 50)
    var taxCode: String? = null,
    /** The hub serving the ward of the default pickup point; it looks after this merchant. */
    @Column(name = "serving_hub_id")
    var servingHubId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: MerchantStatus = MerchantStatus.PENDING_VERIFICATION,
    @Enumerated(EnumType.STRING)
    @Column(name = "status_reason_code", length = 50)
    var statusReasonCode: MerchantStatusReason? = null,
    @Column(name = "status_reason_detail", length = 500)
    var statusReasonDetail: String? = null,
    @Column(name = "status_changed_at", nullable = false)
    var statusChangedAt: Instant = Instant.now(),
    @Column(name = "status_changed_by_user_id")
    var statusChangedByUserId: UUID? = null,
    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,
    @Column(name = "verified_by_user_id")
    var verifiedByUserId: UUID? = null,
    @Version
    var version: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun canTransitionTo(next: MerchantStatus): Boolean = when (status) {
        MerchantStatus.PENDING_VERIFICATION -> next in setOf(MerchantStatus.ACTIVE, MerchantStatus.REJECTED)
        MerchantStatus.ACTIVE -> next == MerchantStatus.SUSPENDED
        MerchantStatus.SUSPENDED -> next in setOf(MerchantStatus.ACTIVE, MerchantStatus.REJECTED)
        MerchantStatus.REJECTED -> false
    }
}

@Entity
@Table(name = "merchant_status_history", schema = "partners")
class MerchantStatusHistory(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    var fromStatus: MerchantStatus? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    var toStatus: MerchantStatus = MerchantStatus.PENDING_VERIFICATION,
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 50)
    var reasonCode: MerchantStatusReason? = null,
    @Column(name = "reason_detail", length = 500)
    var reasonDetail: String? = null,
    @Column(name = "changed_by_user_id")
    var changedByUserId: UUID? = null,
    @Column(name = "changed_at", nullable = false)
    var changedAt: Instant = Instant.now(),
)
