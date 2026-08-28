package com.pavex.auth.event

data class EmailVerificationRequestedEvent(
    val email: String,
    val rawToken: String,
)
