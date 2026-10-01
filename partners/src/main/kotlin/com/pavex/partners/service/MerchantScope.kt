package com.pavex.partners.service

import com.pavex.partners.entity.Merchant
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Which merchants a staff member may see: head office (scope.all-hubs) sees every merchant, hub
 * staff only those their hubs look after. A merchant no hub looks after yet is head office's.
 */
data class MerchantScope(val allHubs: Boolean, val hubIds: Set<UUID>) {
    fun covers(merchant: Merchant): Boolean = allHubs || merchant.servingHubId in hubIds

    fun covers(hubId: UUID): Boolean = allHubs || hubId in hubIds
}

@Component
class MerchantScopeResolver(
    private val hubs: HubDirectoryClient,
) {
    fun resolve(authentication: Authentication): MerchantScope {
        if (authentication.authorities.any { it.authority == ALL_HUBS }) return MerchantScope(true, emptySet())
        val userId = UUID.fromString(authentication.name)
        return MerchantScope(false, hubs.hubsOf(userId))
    }

    companion object {
        const val ALL_HUBS = "scope.all-hubs"
    }
}
