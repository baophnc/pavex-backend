package com.pavex.contracts.identity

import java.util.UUID

/**
 * Cross-service contract describing whether an Identity account is allowed to
 * submit a merchant application. Partners owns merchant state; Identity only
 * answers canonical account/profile prerequisites.
 */
data class MerchantEligibilityResponse(
    val userId: UUID,
    val status: String,
    val active: Boolean,
    val emailVerified: Boolean,
    val profileStatus: String,
    val eligible: Boolean,
    val reasons: Set<String> = emptySet(),
)
