package com.pavex.contracts.identity

import java.util.UUID

data class IdentityUserReference(
    val userId: UUID,
    val displayName: String? = null,
)
