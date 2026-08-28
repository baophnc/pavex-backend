package com.pavex.auth.dto.auth

import java.time.Instant
import java.util.UUID

data class RegisterResponse(

    val id: UUID,

    val email: String,

    val status: String,

    val emailVerified: Boolean,

    val emailVerificationRequired:
    Boolean,

    val createdAt: Instant,
)