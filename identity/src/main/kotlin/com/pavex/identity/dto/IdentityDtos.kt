package com.pavex.identity.dto

import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateIdentityProfileRequest(
    @field:Size(max = 100) val firstName: String? = null,
    @field:Size(max = 100) val lastName: String? = null,
    @field:Size(max = 200) val displayName: String? = null,
    @field:Size(max = 2048) val avatarUrl: String? = null,
)

data class IdentityProfileResponse(
    val userId: UUID,
    val email: String,
    val status: String,
    val emailVerified: Boolean,
    val firstName: String?,
    val lastName: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val authorizationVersion: Long,
)
