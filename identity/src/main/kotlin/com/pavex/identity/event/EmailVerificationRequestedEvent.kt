package com.pavex.identity.event

data class EmailVerificationRequestedEvent(
    val email: String,
    val rawToken: String,
)
