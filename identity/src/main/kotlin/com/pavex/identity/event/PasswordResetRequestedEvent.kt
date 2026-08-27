package com.pavex.identity.event

data class PasswordResetRequestedEvent(
    val email: String,
    val rawToken: String,
)
