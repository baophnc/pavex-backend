package com.pavex.contracts.identity

import java.util.UUID

data class ProvisionIdentityRequest(
    val userId: UUID,
    val email: String,
    val emailVerified: Boolean = false,
    val status: String = "ACTIVE",
    val roles: Set<String> = emptySet(),
)
