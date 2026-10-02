package com.pavex.partners.service

import com.pavex.common.api.OneBasedPagination
import com.pavex.partners.dto.AdminMerchantResponse
import com.pavex.partners.dto.ChangeMerchantStatusRequest
import com.pavex.partners.dto.MerchantListResponse
import com.pavex.partners.dto.MerchantProfileRequest
import com.pavex.partners.dto.MerchantProfileResponse
import com.pavex.partners.dto.MerchantStatusHistoryResponse
import com.pavex.partners.dto.OrderCapabilityResponse
import com.pavex.partners.entity.Merchant
import com.pavex.partners.entity.MerchantStatus
import com.pavex.partners.entity.MerchantStatusHistory
import com.pavex.partners.entity.MerchantRoleOutboxMessage
import com.pavex.partners.exception.ApiException
import com.pavex.partners.repository.MerchantPickupAddressRepository
import com.pavex.partners.repository.MerchantRepository
import com.pavex.partners.repository.MerchantStatusHistoryRepository
import com.pavex.partners.repository.MerchantRoleOutboxRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Service
class MerchantService(
    private val merchants: MerchantRepository,
    private val history: MerchantStatusHistoryRepository,
    private val roleOutbox: MerchantRoleOutboxRepository,
    private val pickupAddresses: MerchantPickupAddressRepository,
    private val identityClient: IdentityMerchantGateway,
    private val jdbcTemplate: JdbcTemplate,
    /** Files a new application under the hub of the applicant's address (absent in unit tests). */
    private val servingHubs: MerchantServingHubService? = null,
) {
    @Transactional
    fun create(userId: UUID, request: MerchantProfileRequest): MerchantProfileResponse {
        val eligibility = identityClient.findMerchantEligibility(userId)
            ?: throw ApiException(HttpStatus.CONFLICT, "merchant_application_not_eligible", ELIGIBILITY_MESSAGE)
        if (!eligibility.eligible) {
            throw ApiException(HttpStatus.CONFLICT, "merchant_application_not_eligible", ELIGIBILITY_MESSAGE)
        }
        if (merchants.findByUserId(userId) != null) {
            throw ApiException(HttpStatus.CONFLICT, "merchant_profile_exists", "Merchant profile already exists.")
        }

        val now = Instant.now()
        val merchant = Merchant(
            merchantCode = nextMerchantCode(),
            userId = userId,
            businessName = request.businessName.trim(),
            contactName = request.contactName.trim(),
            contactPhone = request.contactPhone.trim(),
            contactEmail = normalize(request.contactEmail),
            taxCode = normalize(request.taxCode),
            businessType = request.businessType,
            registrationNumber = normalize(request.registrationNumber),
            representativeName = normalize(request.representativeName),
            registeredAddress = normalize(request.registeredAddress),
            salesChannel = normalize(request.salesChannel),
            productCategory = normalize(request.productCategory),
            expectedMonthlyOrders = request.expectedMonthlyOrders,
            statusChangedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        return try {
            val saved = merchants.saveAndFlush(merchant)
            history.save(
                MerchantStatusHistory(
                    merchantId = saved.id,
                    toStatus = MerchantStatus.PENDING_VERIFICATION,
                    changedByUserId = userId,
                    changedAt = now,
                ),
            )
            servingHubs?.refreshAfterCommit(saved.id)
            saved.toResponse()
        } catch (_: DataIntegrityViolationException) {
            throw ApiException(HttpStatus.CONFLICT, "merchant_profile_exists", "Merchant profile already exists.")
        }
    }

    @Transactional(readOnly = true)
    fun getMine(userId: UUID): MerchantProfileResponse = findByUser(userId).toResponse()

    @Transactional(readOnly = true)
    fun findAccountByUser(userId: UUID): Merchant? = merchants.findByUserId(userId)

    @Transactional
    fun updateMine(userId: UUID, request: MerchantProfileRequest): MerchantProfileResponse {
        val merchant = findByUser(userId)
        if (merchant.status == MerchantStatus.PENDING_VERIFICATION) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "merchant_profile_under_review",
                "Merchant profile cannot be changed while verification is in progress.",
            )
        }
        val businessName = request.businessName.trim()
        val taxCode = normalize(request.taxCode)
        val registrationNumber = normalize(request.registrationNumber)
        val representativeName = normalize(request.representativeName)
        // A form sent without the newer fields keeps what is stored rather than clearing it.
        val businessType = request.businessType ?: merchant.businessType
        val verifiedFieldsChanged = merchant.businessName != businessName || merchant.taxCode != taxCode ||
            merchant.businessType != businessType ||
            (registrationNumber != null && merchant.registrationNumber != registrationNumber) ||
            (representativeName != null && merchant.representativeName != representativeName)
        if (merchant.status in setOf(MerchantStatus.ACTIVE, MerchantStatus.SUSPENDED) && verifiedFieldsChanged) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "merchant_verification_data_locked",
                "Business name, tax code, business form, registration and representative cannot be changed after verification.",
            )
        }
        merchant.businessName = businessName
        merchant.contactName = request.contactName.trim()
        merchant.contactPhone = request.contactPhone.trim()
        merchant.contactEmail = normalize(request.contactEmail)
        merchant.taxCode = taxCode
        merchant.businessType = businessType
        registrationNumber?.let { merchant.registrationNumber = it }
        representativeName?.let { merchant.representativeName = it }
        request.registeredAddress?.let { merchant.registeredAddress = normalize(it) }
        request.salesChannel?.let { merchant.salesChannel = normalize(it) }
        request.productCategory?.let { merchant.productCategory = normalize(it) }
        request.expectedMonthlyOrders?.let { merchant.expectedMonthlyOrders = it }
        merchant.updatedAt = Instant.now()
        return merchants.save(merchant).toResponse()
    }

    @Transactional
    fun resubmit(userId: UUID): MerchantProfileResponse {
        val merchant = findByUser(userId)
        if (merchant.status != MerchantStatus.REJECTED) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "invalid_merchant_status",
                "Only rejected merchants can resubmit verification.",
            )
        }
        val previous = merchant.status
        val now = Instant.now()
        merchant.status = MerchantStatus.PENDING_VERIFICATION
        merchant.statusReasonCode = null
        merchant.statusReasonDetail = null
        merchant.statusChangedAt = now
        merchant.statusChangedByUserId = userId
        merchant.verifiedAt = null
        merchant.verifiedByUserId = null
        merchant.updatedAt = now
        history.save(
            MerchantStatusHistory(
                merchantId = merchant.id,
                fromStatus = previous,
                toStatus = merchant.status,
                changedByUserId = userId,
                changedAt = now,
            ),
        )
        return merchants.save(merchant).toResponse()
    }

    @Transactional(readOnly = true)
    fun listAdmin(
        scope: MerchantScope,
        page: Int,
        pageSize: Int,
        status: MerchantStatus?,
        search: String?,
        hubId: UUID? = null,
    ): MerchantListResponse {
        if (hubId != null && !scope.covers(hubId)) {
            throw ApiException(HttpStatus.FORBIDDEN, "hub_outside_scope", "The hub is outside your assigned hubs.")
        }
        val pagination = OneBasedPagination.normalize(page, pageSize)
        val result = merchants.search(
            status,
            normalize(search).orEmpty(),
            scope.allHubs,
            // JPQL needs a non-empty IN list; nobody's hub is the zero id.
            scope.hubIds.ifEmpty { setOf(ZERO_UUID) },
            hubId,
            PageRequest.of(
                pagination.zeroBasedPage,
                pagination.pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"),
            ),
        )
        return MerchantListResponse(
            result.content.map { it.toResponse() },
            pagination.page,
            pagination.pageSize,
            result.totalElements,
            result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getAdmin(id: UUID, scope: MerchantScope = HEAD_OFFICE): AdminMerchantResponse {
        val merchant = merchants.findById(id).orElseThrow { merchantNotFound() }
        if (!scope.covers(merchant)) throw merchantNotFound()
        return AdminMerchantResponse(
            merchant.toResponse(),
            history.findAllByMerchantIdOrderByChangedAtDesc(id).map {
                MerchantStatusHistoryResponse(
                    it.id,
                    it.fromStatus,
                    it.toStatus,
                    it.reasonCode,
                    it.reasonDetail,
                    it.changedByUserId,
                    it.changedAt,
                )
            },
        )
    }

    @Transactional
    fun changeStatus(
        id: UUID,
        adminUserId: UUID,
        request: ChangeMerchantStatusRequest,
        scope: MerchantScope = HEAD_OFFICE,
    ): AdminMerchantResponse {
        val merchant = merchants.findById(id).orElseThrow { merchantNotFound() }
        if (!scope.covers(merchant)) throw merchantNotFound()
        val next = try {
            MerchantStatus.valueOf(request.status.trim().uppercase(Locale.ROOT))
        } catch (_: IllegalArgumentException) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_merchant_status", "Merchant status is invalid.")
        }
        if (next == merchant.status) return getAdmin(id, scope)
        if (!merchant.canTransitionTo(next)) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "invalid_merchant_status_transition",
                "Merchant status transition is not allowed.",
            )
        }
        if (next in setOf(MerchantStatus.SUSPENDED, MerchantStatus.REJECTED) && request.reasonCode == null) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "merchant_status_reason_required",
                "A reason code is required for rejection or suspension.",
            )
        }

        val previous = merchant.status
        val now = Instant.now()
        merchant.status = next
        merchant.statusReasonCode = request.reasonCode
        merchant.statusReasonDetail = normalize(request.reasonDetail)
        merchant.statusChangedAt = now
        merchant.statusChangedByUserId = adminUserId
        merchant.updatedAt = now
        if (next == MerchantStatus.ACTIVE && merchant.verifiedAt == null) {
            merchant.verifiedAt = now
            merchant.verifiedByUserId = adminUserId
        } else if (next == MerchantStatus.REJECTED) {
            merchant.verifiedAt = null
            merchant.verifiedByUserId = null
        }
        merchants.save(merchant)
        history.save(
            MerchantStatusHistory(
                merchantId = merchant.id,
                fromStatus = previous,
                toStatus = next,
                reasonCode = request.reasonCode,
                reasonDetail = normalize(request.reasonDetail),
                changedByUserId = adminUserId,
                changedAt = now,
            ),
        )
        roleOutbox.supersedePendingRoleChanges(merchant.userId, now)
        roleOutbox.save(
            MerchantRoleOutboxMessage(
                merchantId = merchant.id,
                userId = merchant.userId,
                enabled = next == MerchantStatus.ACTIVE,
                assignedByUserId = adminUserId,
                occurredAt = now,
                availableAt = now,
            ),
        )
        return getAdmin(id)
    }

    @Transactional(readOnly = true)
    fun orderCapability(userId: UUID): OrderCapabilityResponse {
        val merchant = merchants.findByUserId(userId)
            ?: return OrderCapabilityResponse(
                null, null, userId, null, false, null, listOf("MERCHANT_REQUIRED"),
            )
        val eligibility = identityClient.findMerchantEligibility(userId)
        return capabilityOf(merchant, identityEligible = eligibility?.eligible == true)
    }

    /**
     * What stops [merchant] from creating orders. [identityEligible] is null when Identity could
     * not be asked; the identity restriction is then left out and the caller must say so.
     */
    @Transactional(readOnly = true)
    fun capabilityOf(merchant: Merchant, identityEligible: Boolean?): OrderCapabilityResponse {
        val restrictions = linkedSetOf<String>()
        when (merchant.status) {
            MerchantStatus.ACTIVE -> Unit
            MerchantStatus.SUSPENDED -> restrictions += "MERCHANT_SUSPENDED"
            else -> restrictions += "MERCHANT_NOT_ACTIVE"
        }
        if (identityEligible == false) restrictions += "IDENTITY_NOT_ELIGIBLE"
        val defaultPickup = pickupAddresses.findFirstByMerchantIdAndIsDefaultTrue(merchant.id)
        if (defaultPickup == null) restrictions += "DEFAULT_PICKUP_REQUIRED"
        return OrderCapabilityResponse(
            merchant.id,
            merchant.merchantCode,
            merchant.userId,
            merchant.status,
            restrictions.isEmpty(),
            defaultPickup?.id,
            restrictions.toList(),
        )
    }

    fun findActiveByUser(userId: UUID): Merchant {
        val merchant = findByUser(userId)
        if (merchant.status != MerchantStatus.ACTIVE) {
            throw ApiException(
                HttpStatus.FORBIDDEN,
                "merchant_not_active",
                "Only active merchants can manage pickup addresses.",
            )
        }
        return merchant
    }

    private fun nextMerchantCode(): String {
        val value = requireNotNull(
            jdbcTemplate.queryForObject("SELECT nextval('partners.merchant_code_seq')", Long::class.java),
        )
        return "PVM%06d".format(value)
    }

    private fun findByUser(userId: UUID): Merchant =
        merchants.findByUserId(userId) ?: throw merchantNotFound()

    private fun merchantNotFound() =
        ApiException(HttpStatus.NOT_FOUND, "merchant_not_found", "Merchant profile was not found.")

    private fun normalize(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun Merchant.toResponse() = MerchantProfileResponse(
        id,
        merchantCode,
        userId,
        businessName,
        contactName,
        contactPhone,
        contactEmail,
        taxCode,
        status,
        statusReasonCode,
        statusReasonDetail,
        statusChangedAt,
        verifiedAt,
        createdAt,
        updatedAt,
        version,
        servingHubId,
        businessType,
        registrationNumber,
        representativeName,
        registeredAddress,
        salesChannel,
        productCategory,
        expectedMonthlyOrders,
    )

    companion object {
        private val ZERO_UUID = UUID(0, 0)
        private val HEAD_OFFICE = MerchantScope(allHubs = true, hubIds = emptySet())

        private const val ELIGIBILITY_MESSAGE =
            "Verify your email and complete your customer profile before applying as a merchant."
    }
}
