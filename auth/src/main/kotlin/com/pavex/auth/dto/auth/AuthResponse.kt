package com.pavex.auth.dto.auth

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    /** The account still has the temporary password an administrator issued; ask for a new one. */
    val passwordChangeRequired: Boolean = false,
)