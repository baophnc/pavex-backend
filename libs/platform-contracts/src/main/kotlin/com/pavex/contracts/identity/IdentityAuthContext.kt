package com.pavex.contracts.identity

import java.util.UUID

data class IdentityAuthContext(
    val userId: UUID,
    val status: String,
    val active: Boolean,
    val emailVerified: Boolean,
    val roles: Set<String>,
    val permissions: Set<String>,
    val authorizationVersion: Long = 0,
)
