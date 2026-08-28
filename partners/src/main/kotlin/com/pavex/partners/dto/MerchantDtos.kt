package com.pavex.partners.dto

import com.pavex.partners.entity.MerchantStatus
import com.pavex.partners.entity.MerchantStatusReason
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class MerchantProfileRequest(
    @field:NotBlank @field:Size(max = 255)
    val businessName: String,
    @field:NotBlank @field:Size(max = 200)
    val contactName: String,
    @field:NotBlank @field:Size(max = 30)
    val contactPhone: String,
    @field:Email @field:Size(max = 320)
    val contactEmail: String? = null,
    @field:Size(max = 50)
    val taxCode: String? = null,
)

data class MerchantProfileResponse(
    val id: UUID,
    val merchantCode: String,
    val userId: UUID,
    val businessName: String,
    val contactName: String,
    val contactPhone: String,
    val contactEmail: String?,
    val taxCode: String?,
    val status: MerchantStatus,
    val statusReasonCode: MerchantStatusReason?,
    val statusReasonDetail: String?,
    val statusChangedAt: Instant,
    val verifiedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class ChangeMerchantStatusRequest(
    @field:NotBlank
    val status: String,
    val reasonCode: MerchantStatusReason? = null,
    @field:Size(max = 500)
    val reasonDetail: String? = null,
)

data class MerchantStatusHistoryResponse(
    val id: UUID,
    val fromStatus: MerchantStatus?,
    val toStatus: MerchantStatus,
    val reasonCode: MerchantStatusReason?,
    val reasonDetail: String?,
    val changedByUserId: UUID?,
    val changedAt: Instant,
)

data class AdminMerchantResponse(
    val merchant: MerchantProfileResponse,
    val history: List<MerchantStatusHistoryResponse>,
)

data class MerchantListResponse(
    val content: List<MerchantProfileResponse>,
    val page: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int,
)
