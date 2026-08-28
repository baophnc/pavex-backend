package com.pavex.partners.service

import com.pavex.contracts.identity.MerchantEligibilityResponse
import com.pavex.contracts.identity.MerchantRoleSyncRequest
import java.util.UUID

interface IdentityMerchantGateway {
    fun findMerchantEligibility(userId: UUID): MerchantEligibilityResponse?
    fun synchronizeMerchantRole(userId: UUID, request: MerchantRoleSyncRequest)
}
