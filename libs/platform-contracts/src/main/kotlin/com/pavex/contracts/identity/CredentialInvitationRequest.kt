package com.pavex.contracts.identity

import java.util.UUID

data class CredentialInvitationRequest(
    val userId: UUID,
    val email: String,
)
