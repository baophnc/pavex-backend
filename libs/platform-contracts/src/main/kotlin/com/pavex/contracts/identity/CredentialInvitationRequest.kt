package com.pavex.contracts.identity

import java.util.UUID

data class CredentialInvitationRequest(
    val userId: UUID,
    val email: String,
    /** Staff are invited to the management portal, customers and shops to the customer portal. */
    val staff: Boolean = false,
)
