package com.pavex.auth.event

data class PasswordResetRequestedEvent(
    val email: String,
    val rawToken: String,
    val portal: PasswordResetPortal = PasswordResetPortal.CLIENT,
)
