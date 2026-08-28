package com.pavex.auth.event

data class InvitationRequestedEvent(
    val email: String,
    val rawToken: String,
)
