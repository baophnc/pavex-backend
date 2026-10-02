package com.pavex.partners.service

import com.pavex.contracts.identity.MerchantEligibilityResponse
import com.pavex.contracts.identity.MerchantRoleSyncRequest
import com.pavex.partners.entity.Merchant
import com.pavex.partners.entity.MerchantPickupAddress
import com.pavex.partners.entity.MerchantRoleOutboxMessage
import com.pavex.partners.entity.MerchantStatus
import com.pavex.partners.entity.Province
import com.pavex.partners.entity.Ward
import com.pavex.partners.exception.IdentityServiceUnavailableException
import com.pavex.partners.repository.MerchantPickupAddressRepository
import com.pavex.partners.repository.MerchantRepository
import com.pavex.partners.repository.MerchantRoleOutboxRepository
import com.pavex.partners.repository.MerchantStatusHistoryRepository
import com.pavex.partners.repository.ProvinceRepository
import com.pavex.partners.repository.WardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AdminMerchantReadinessServiceTest {
    private val merchant = Merchant(merchantCode = "PVM000001", userId = UUID.randomUUID(), status = MerchantStatus.ACTIVE)
    private val usable = pickup("Kho chính", phone = "0912345678", isDefault = true)
    private val landline = pickup("Kho phụ", phone = "028 3823 4567", isDefault = false)

    @Test
    fun `shows the merchant's restrictions and which pickup addresses can be a sender`() {
        val result = service(eligible = true, addresses = listOf(usable, landline)).readiness(merchant.id)

        assertEquals(true, result.canCreateOrder)
        assertTrue(result.identityChecked)
        assertEquals(usable.id, result.defaultPickupAddressId)
        assertEquals(listOf(true, false), result.pickupAddresses.map { it.usableAsSender })
        assertEquals("Thành phố Đà Nẵng", result.pickupAddresses.first().provinceName)
        assertEquals("Phường Hải Châu", result.pickupAddresses.first().wardName)
    }

    @Test
    fun `an unreachable identity service leaves the verdict open instead of failing`() {
        val result = service(eligible = null, addresses = listOf(usable)).readiness(merchant.id)

        assertFalse(result.identityChecked)
        assertNull(result.canCreateOrder)
        assertTrue(result.restrictions.isEmpty())
    }

    @Test
    fun `a missing default pickup still blocks while identity is unknown`() {
        val result = service(eligible = null, addresses = emptyList()).readiness(merchant.id)

        assertEquals(false, result.canCreateOrder)
        assertEquals(listOf("DEFAULT_PICKUP_REQUIRED"), result.restrictions)
    }

    @Test
    fun `an approval that has not reached identity yet is shown with its last error`() {
        val pending = MerchantRoleOutboxMessage(
            merchantId = merchant.id,
            userId = merchant.userId,
            enabled = true,
            occurredAt = Instant.parse("2026-10-01T08:00:00Z"),
            attemptCount = 3,
            lastError = "Identity unavailable",
        )

        val synced = service(eligible = true, addresses = listOf(usable)).readiness(merchant.id)
        val waiting = service(eligible = true, addresses = listOf(usable), pendingRole = pending).readiness(merchant.id)

        assertNull(synced.roleSync)
        assertEquals(true, waiting.roleSync?.enabled)
        assertEquals(3, waiting.roleSync?.attempts)
        assertEquals("Identity unavailable", waiting.roleSync?.lastError)
    }

    @Test
    fun `hub staff only open merchants their hub looks after`() {
        val hub = UUID.randomUUID()
        merchant.servingHubId = hub
        val service = service(eligible = true, addresses = listOf(usable))

        service.readiness(merchant.id, MerchantScope(allHubs = false, hubIds = setOf(hub)))
        val other = org.junit.jupiter.api.assertThrows<com.pavex.partners.exception.ApiException> {
            service.readiness(merchant.id, MerchantScope(allHubs = false, hubIds = setOf(UUID.randomUUID())))
        }
        assertEquals("merchant_not_found", other.code)

        merchant.servingHubId = null
        assertFalse(MerchantScope(allHubs = false, hubIds = setOf(hub)).covers(merchant))
        assertTrue(MerchantScope(allHubs = true, hubIds = emptySet()).covers(merchant))
    }

    private fun pickup(name: String, phone: String, isDefault: Boolean) = MerchantPickupAddress(
        merchantId = merchant.id,
        name = name,
        contactName = "Người gửi",
        contactPhone = phone,
        addressLine = "1 Trần Phú",
        provinceCode = "48",
        wardCode = "20194",
        isDefault = isDefault,
    )

    /** [eligible] null simulates Identity being unavailable. */
    private fun service(
        eligible: Boolean?,
        addresses: List<MerchantPickupAddress>,
        pendingRole: MerchantRoleOutboxMessage? = null,
    ): AdminMerchantReadinessService {
        val merchants = proxy<MerchantRepository> { method, _ ->
            if (method == "findById") Optional.of(merchant) else defaultValue(method)
        }
        val pickups = proxy<MerchantPickupAddressRepository> { method, _ ->
            when (method) {
                "findAllByMerchantIdOrderByIsDefaultDescCreatedAtAsc" -> addresses
                "findFirstByMerchantIdAndIsDefaultTrue" -> addresses.firstOrNull { it.isDefault }
                else -> defaultValue(method)
            }
        }
        val identity = object : IdentityMerchantGateway {
            override fun findMerchantEligibility(userId: UUID): MerchantEligibilityResponse {
                val value = eligible ?: throw IdentityServiceUnavailableException()
                return MerchantEligibilityResponse(userId, "ACTIVE", true, true, "COMPLETED", value)
            }

            override fun synchronizeMerchantRole(userId: UUID, request: MerchantRoleSyncRequest) = Unit
        }
        val provinces = proxy<ProvinceRepository> { method, _ ->
            if (method == "findAllById") listOf(Province(code = "48", name = "Đà Nẵng", fullName = "Thành phố Đà Nẵng")) else defaultValue(method)
        }
        val wards = proxy<WardRepository> { method, _ ->
            if (method == "findAllById") listOf(Ward(code = "20194", name = "Hải Châu", fullName = "Phường Hải Châu", provinceCode = "48")) else defaultValue(method)
        }
        val roleOutbox = proxy<MerchantRoleOutboxRepository> { method, _ ->
            if (method == "findFirstByMerchantIdAndProcessedAtIsNullOrderByOccurredAtDesc") pendingRole else defaultValue(method)
        }
        val merchantService = MerchantService(
            merchants,
            proxy<MerchantStatusHistoryRepository> { method, _ -> defaultValue(method) },
            roleOutbox,
            pickups,
            identity,
            JdbcTemplate(),
        )
        return AdminMerchantReadinessService(merchants, merchantService, pickups, identity, provinces, wards, roleOutbox)
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
