package com.pavex.partners.service

import com.pavex.contracts.identity.MerchantEligibilityResponse
import com.pavex.contracts.identity.MerchantRoleSyncRequest
import com.pavex.partners.dto.ChangeMerchantStatusRequest
import com.pavex.partners.dto.MerchantProfileRequest
import com.pavex.partners.entity.MerchantBusinessType
import com.pavex.partners.exception.ApiException
import org.junit.jupiter.api.assertThrows
import com.pavex.partners.entity.Merchant
import com.pavex.partners.entity.MerchantPickupAddress
import com.pavex.partners.entity.MerchantRoleOutboxMessage
import com.pavex.partners.entity.MerchantStatus
import com.pavex.partners.entity.MerchantStatusReason
import com.pavex.partners.repository.MerchantPickupAddressRepository
import com.pavex.partners.repository.MerchantRepository
import com.pavex.partners.repository.MerchantRoleOutboxRepository
import com.pavex.partners.repository.MerchantStatusHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.lang.reflect.Proxy
import java.util.Optional
import java.util.UUID

class MerchantServiceTest {
    @Test
    fun `ordinary customer cannot create an order`() {
        val userId = UUID.randomUUID()
        val result = service(merchantByUser = null).orderCapability(userId)

        assertFalse(result.canCreateOrder)
        assertEquals(listOf("MERCHANT_REQUIRED"), result.restrictions)
    }

    @Test
    fun `active eligible merchant with default pickup can create an order`() {
        val merchant = Merchant(
            merchantCode = "PVM000001",
            userId = UUID.randomUUID(),
            status = MerchantStatus.ACTIVE,
        )
        val pickup = MerchantPickupAddress(merchantId = merchant.id, isDefault = true)

        val result = service(merchantByUser = merchant, defaultPickup = pickup)
            .orderCapability(merchant.userId)

        assertTrue(result.canCreateOrder)
        assertTrue(result.restrictions.isEmpty())
        assertEquals(pickup.id, result.defaultPickupAddressId)
    }

    @Test
    fun `suspending merchant persists a role removal in the same operation`() {
        val merchant = Merchant(
            merchantCode = "PVM000001",
            userId = UUID.randomUUID(),
            status = MerchantStatus.ACTIVE,
        )
        var capturedOutbox: MerchantRoleOutboxMessage? = null
        var supersededUserId: UUID? = null
        val service = service(
            merchantByUser = merchant,
            merchantById = merchant,
            captureOutbox = { capturedOutbox = it },
            captureSupersede = { supersededUserId = it },
        )

        service.changeStatus(
            merchant.id,
            UUID.randomUUID(),
            ChangeMerchantStatusRequest("SUSPENDED", MerchantStatusReason.FRAUD_REVIEW),
        )

        assertEquals(MerchantStatus.SUSPENDED, merchant.status)
        assertFalse(requireNotNull(capturedOutbox).enabled)
        assertEquals(merchant.userId, capturedOutbox.userId)
        assertEquals(merchant.userId, supersededUserId, "Older pending role changes must be superseded first")
    }

    @Test
    fun `a verified shop updates where it sells but not who represents it`() {
        val merchant = Merchant(
            merchantCode = "PVM000001",
            userId = UUID.randomUUID(),
            businessName = "Cửa hàng Hoa Sen",
            contactName = "Bùi Ngọc Mai",
            contactPhone = "0912000041",
            taxCode = "0109000041",
            businessType = MerchantBusinessType.HOUSEHOLD,
            representativeName = "Bùi Ngọc Mai",
            status = MerchantStatus.ACTIVE,
        )
        val service = service(merchantByUser = merchant, merchantById = merchant)
        val base = MerchantProfileRequest(
            businessName = "Cửa hàng Hoa Sen",
            contactName = "Bùi Ngọc Mai",
            contactPhone = "0912000041",
            taxCode = "0109000041",
        )

        val updated = service.updateMine(merchant.userId, base.copy(salesChannel = "https://shopee.vn/hoasen", expectedMonthlyOrders = 300))
        assertEquals("https://shopee.vn/hoasen", updated.salesChannel)
        assertEquals(MerchantBusinessType.HOUSEHOLD, updated.businessType, "A form without the business form keeps it")

        val filled = service.updateMine(merchant.userId, base.copy(registrationNumber = "01A8000123"))
        assertEquals("01A8000123", filled.registrationNumber, "A detail never given may be filled in once")

        val locked = assertThrows<ApiException> {
            service.updateMine(merchant.userId, base.copy(representativeName = "Người khác"))
        }
        assertEquals("merchant_verification_data_locked", locked.code)
    }

    private fun service(
        merchantByUser: Merchant?,
        merchantById: Merchant? = null,
        defaultPickup: MerchantPickupAddress? = null,
        captureOutbox: (MerchantRoleOutboxMessage) -> Unit = {},
        captureSupersede: (UUID) -> Unit = {},
    ): MerchantService {
        val merchantRepository = proxy<MerchantRepository> { method, _ ->
            when (method) {
                "findByUserId" -> merchantByUser
                "findById" -> Optional.ofNullable(merchantById)
                "save" -> merchantById ?: merchantByUser
                else -> defaultValue(method)
            }
        }
        val historyRepository = proxy<MerchantStatusHistoryRepository> { method, args ->
            when (method) {
                "findAllByMerchantIdOrderByChangedAtDesc" -> emptyList<Any>()
                "save" -> args?.first()
                else -> defaultValue(method)
            }
        }
        val outboxRepository = proxy<MerchantRoleOutboxRepository> { method, args ->
            when (method) {
                "save" -> {
                    val message = args?.first() as MerchantRoleOutboxMessage
                    captureOutbox(message)
                    message
                }
                "supersedePendingRoleChanges" -> {
                    captureSupersede(args?.first() as UUID)
                    0
                }
                else -> defaultValue(method)
            }
        }
        val pickupRepository = proxy<MerchantPickupAddressRepository> { method, _ ->
            if (method == "findFirstByMerchantIdAndIsDefaultTrue") defaultPickup
            else defaultValue(method)
        }
        val identity = object : IdentityMerchantGateway {
            override fun findMerchantEligibility(userId: UUID): MerchantEligibilityResponse =
                MerchantEligibilityResponse(
                    userId,
                    "ACTIVE",
                    active = true,
                    emailVerified = true,
                    profileStatus = "COMPLETED",
                    eligible = true,
                )

            override fun synchronizeMerchantRole(userId: UUID, request: MerchantRoleSyncRequest) = Unit
        }
        return MerchantService(
            merchantRepository,
            historyRepository,
            outboxRepository,
            pickupRepository,
            identity,
            JdbcTemplate(),
        )
    }

    private inline fun <reified T> proxy(
        crossinline handler: (String, Array<out Any?>?) -> Any?,
    ): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { instance, method, args ->
        when (method.name) {
            "toString" -> "${T::class.simpleName}TestProxy"
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === args?.firstOrNull()
            else -> handler(method.name, args)
        }
    } as T

    private fun defaultValue(method: String): Any? = when {
        method.startsWith("exists") -> false
        method.startsWith("count") -> 0L
        method.startsWith("findAll") -> emptyList<Any>()
        else -> null
    }
}
