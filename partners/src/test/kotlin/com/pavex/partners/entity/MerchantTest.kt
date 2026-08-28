package com.pavex.partners.entity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerchantTest {
    @Test
    fun `pending merchant can only be approved or rejected`() {
        val merchant = Merchant(status = MerchantStatus.PENDING_VERIFICATION)

        assertTrue(merchant.canTransitionTo(MerchantStatus.ACTIVE))
        assertTrue(merchant.canTransitionTo(MerchantStatus.REJECTED))
        assertFalse(merchant.canTransitionTo(MerchantStatus.SUSPENDED))
    }

    @Test
    fun `active merchant can only be suspended`() {
        val merchant = Merchant(status = MerchantStatus.ACTIVE)

        assertTrue(merchant.canTransitionTo(MerchantStatus.SUSPENDED))
        assertFalse(merchant.canTransitionTo(MerchantStatus.REJECTED))
        assertFalse(merchant.canTransitionTo(MerchantStatus.PENDING_VERIFICATION))
    }

    @Test
    fun `suspended merchant can be reactivated or rejected`() {
        val merchant = Merchant(status = MerchantStatus.SUSPENDED)

        assertTrue(merchant.canTransitionTo(MerchantStatus.ACTIVE))
        assertTrue(merchant.canTransitionTo(MerchantStatus.REJECTED))
        assertFalse(merchant.canTransitionTo(MerchantStatus.PENDING_VERIFICATION))
    }

    @Test
    fun `rejected merchant must resubmit instead of admin transition`() {
        val merchant = Merchant(status = MerchantStatus.REJECTED)

        MerchantStatus.entries.forEach { assertFalse(merchant.canTransitionTo(it)) }
    }
}
